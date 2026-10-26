package com.codesupreme.sifarisqrupu.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.core.NestedRuntimeException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class FcmPushService {
    private static final Logger log=LoggerFactory.getLogger(FcmPushService.class);
    private final PushDeliveryPolicy policy;
    private final PushDeviceRepository devices;
    private final PushDeliveryRepository deliveries;
    private final UserRepository users;
    private final AdminRepository admins;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String projectId;
    private FirebaseMessaging messaging;
    private final AtomicBoolean databaseUnavailable=new AtomicBoolean();
    private final AtomicLong nextDatabaseWarningAt=new AtomicLong();
    private final ExecutorService sendExecutor=Executors.newFixedThreadPool(16,task->{
        Thread t=new Thread(task,"fcm-delivery");t.setDaemon(true);return t;
    });
    @PreDestroy public void close() {sendExecutor.shutdown();}
    @PostConstruct public void validateConfiguration() {
        if(enabled) {
            try {messaging();log.info("FCM configured for project {}",projectId);}
            catch(Exception e) {throw new IllegalStateException("FCM enabled but server credentials could not be loaded",e);}
        }
    }

    public FcmPushService(PushDeviceRepository devices, PushDeliveryRepository deliveries, UserRepository users, AdminRepository admins,
        ObjectMapper json, PushDeliveryPolicy policy, @Value("${push.fcm.enabled:false}") boolean enabled,
        @Value("${push.fcm.project-id:bakuappcraft-notifications}") String projectId) {
        this.devices=devices; this.deliveries=deliveries; this.users=users; this.admins=admins; this.json=json;
        this.policy=policy; this.enabled=enabled; this.projectId=projectId;
    }
    public boolean isEnabled() { return enabled; }
    private synchronized FirebaseMessaging messaging() throws java.io.IOException {
        if(messaging==null) {
            FirebaseOptions options=FirebaseOptions.builder().setProjectId(projectId)
                .setCredentials(GoogleCredentials.getApplicationDefault()).setConnectTimeout(5000).setReadTimeout(10000).build();
            messaging=FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options,"push-delivery"));
        }
        return messaging;
    }
    public static String app(String value) {
        if (value==null || !Set.of("ZAKAZ","ELEHBER").contains(value)) throw new IllegalArgumentException("Unsupported appCode");
        return value;
    }
    @Transactional
    public synchronized int enqueue(String appCode, Collection<Long> userIds, String title, String body,
            Map<String,?> data, int ttlSeconds, String eventId) {
        if(!enabled || userIds.isEmpty()) return 0;
        if (!"ADMIN".equals(appCode)) app(appCode);
        long now=System.currentTimeMillis();
        int ttl=Math.max(1,Math.min(86400,ttlSeconds));
        String id=eventId==null ? UUID.randomUUID().toString() : eventId;
        if(id.length()>100) throw new IllegalArgumentException("eventId too long");
        Map<String,String> values=new LinkedHashMap<>();
        data.forEach((k,v)-> {if(v!=null) values.put(k,String.valueOf(v));});
        values.put("title", title==null ? "" : title);
        values.put("body", body==null ? "" : body);
        values.put("eventId",id);
        values.put("sentAt",Long.toString(now));
        values.put("expiresAt",Long.toString(now+ttl*1000L));
        final String payload;
        try { payload=json.writeValueAsString(values); } catch(Exception e) {throw new IllegalArgumentException("Invalid push payload");}
        if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>3500) throw new IllegalArgumentException("Push payload too large");
        int queued=0;
        // One duplicate lookup for the whole event; per-device exists queries
        // flushed every preceding insert and made broadcasts exceed HTTP timeouts.
        Set<Long> alreadyQueued=new HashSet<>(deliveries.findDeviceIdsByEventId(id));
        for(PushDevice device:devices.findByAppCodeAndUserIdInAndEnabledTrue(appCode,userIds)) {
            if(!PushDeliveryPolicy.allowsDevice(values,device)) continue;
            if(!alreadyQueued.add(device.getId())) continue;
            PushDelivery item=new PushDelivery();
            item.setEventId(id); item.setDeviceId(device.getId()); item.setUserId(device.getUserId()); item.setAppCode(appCode);
            Map<String,String> targeted=new LinkedHashMap<>(values);
            targeted.put("ADMIN".equals(appCode) ? "recipientAdminId" : "recipientUserId",device.getUserId().toString());
            try { item.setPayload(json.writeValueAsString(targeted)); } catch(Exception e) {throw new IllegalArgumentException("Invalid target payload");} item.setExpiresAt(now+ttl*1000L); item.setNextAttemptAt(now); item.setState("PENDING");
            deliveries.save(item); queued++;
        }
        return queued;
    }
    // One worker in this deployment; durable rows survive Docker restarts.
    @Scheduled(fixedDelayString="${push.fcm.worker-delay-ms:1000}",scheduler="pushTaskScheduler")
    public void deliverDue() {
        if(!enabled) return;
        final List<PushDelivery> batch;
        try {
            batch=deliveries.findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc("PENDING",System.currentTimeMillis());
        } catch(DataAccessException | TransactionException error) {
            databaseFailure("fetch",error);return;
        }
        var batchDatabaseFailure=new AtomicBoolean();
        CompletableFuture.allOf(batch.stream().map(delivery->CompletableFuture.runAsync(()->{
            try {deliverOne(delivery);}
            catch(DataAccessException | TransactionException error) {
                // Keep the durable row pending. A database outage is not a
                // Firebase failure and must never exhaust its retry budget.
                batchDatabaseFailure.set(true);databaseFailure("delivery",error);
            }
        },sendExecutor))
            .toArray(CompletableFuture[]::new)).join();
        if(!batchDatabaseFailure.get() && databaseUnavailable.compareAndSet(true,false)) {
            nextDatabaseWarningAt.set(0);
            log.info("FCM database recovered. Pending delivery processing resumed.");
        }
    }
    private void databaseFailure(String stage,NestedRuntimeException error) {
        databaseUnavailable.set(true);
        long now=System.currentTimeMillis(),next=nextDatabaseWarningAt.get();
        if(now>=next && nextDatabaseWarningAt.compareAndSet(next,now+60000)) {
            log.warn("FCM database unavailable. stage={}, cause={}. Pending deliveries retained for retry.",
                stage,error.getMostSpecificCause().getClass().getSimpleName());
        }
    }
    private void expire(PushDelivery delivery,String reason) {
        delivery.setState("EXPIRED");delivery.setLastError(reason);deliveries.save(delivery);
        if(isLottery(delivery)) log.info("FCM lottery skipped. eventId={}, deliveryId={}, app={}, deviceId={}, reason={}",
            delivery.getEventId(),delivery.getId(),delivery.getAppCode(),delivery.getDeviceId(),reason);
    }
    private static boolean isLottery(PushDelivery delivery) {
        return delivery.getEventId()!=null && delivery.getEventId().startsWith("lottery-");
    }
    private void deliverOne(PushDelivery delivery) {
            long now=System.currentTimeMillis();
            PushDevice device=devices.findById(delivery.getDeviceId()).orElse(null);
            boolean userActive="ADMIN".equals(delivery.getAppCode())
                ? admins.findById(delivery.getUserId()).map(a->!Boolean.TRUE.equals(a.getIsDisable()) && !Boolean.TRUE.equals(a.getIsMutedNotifications())).orElse(false)
                : users.findById(delivery.getUserId()).map(u->!Boolean.TRUE.equals(u.getIsDisable())).orElse(false);
            String ineligible=delivery.getExpiresAt()<=now ? "TTL_EXPIRED" : device==null ? "DEVICE_MISSING" :
                !device.isEnabled() ? "DEVICE_DISABLED" : !userActive ? "ACCOUNT_DISABLED" :
                !Objects.equals(device.getUserId(),delivery.getUserId()) || !Objects.equals(device.getAppCode(),delivery.getAppCode()) ? "DEVICE_ACCOUNT_CHANGED" : null;
            if(ineligible!=null) {expire(delivery,ineligible);return;}
            try {
                Map<String,String> data=json.readValue(delivery.getPayload(),new TypeReference<Map<String,String>>(){});
                if(!PushDeliveryPolicy.allowsDevice(data,device) || !policy.allows(data,delivery.getUserId(),delivery.getAppCode())) {
                    expire(delivery,"POLICY_REJECTED");return;
                }
                long remaining=delivery.getExpiresAt()-now;
                Message.Builder message=Message.builder().setToken(device.getToken()).putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(remaining).setRestrictedPackageName("ZAKAZ".equals(device.getAppCode()) ?
                            "com.bakuappcraft.zakazqrupu" : "ADMIN".equals(device.getAppCode()) ? "com.biglikuryer.sifarisqrupuadmin" : "com.bakuappcraft.elehber").build());
                if("ios".equals(device.getPlatform())) {
                    Aps.Builder aps=Aps.builder().setContentAvailable(true);
                    if(!data.getOrDefault("title", "").isBlank()) {
                        aps.setAlert(ApsAlert.builder().setTitle(data.get("title")).setBody(data.get("body")).build()).setSound(iosSound(device.getAppCode(),data));
                    }
                    message.setApnsConfig(ApnsConfig.builder().setAps(aps.build())
                        .putHeader("apns-expiration", Long.toString(delivery.getExpiresAt()/1000)).build());
                }
                messaging().send(message.build());
                delivery.setState("SENT"); delivery.setLastError(null);
            } catch(DataAccessException | TransactionException error) {
                throw error;
            } catch(Exception error) {
                MessagingErrorCode code=error instanceof FirebaseMessagingException ? ((FirebaseMessagingException)error).getMessagingErrorCode() : null;
                delivery.setLastError(code==null ? error.getClass().getSimpleName() : code.name());
                if(code==MessagingErrorCode.UNREGISTERED) {
                    // Token may have rotated while the HTTP request was in flight.
                    PushDevice current=devices.findById(device.getId()).orElse(null);
                    if(current!=null && current.isEnabled() && !Objects.equals(current.getToken(),device.getToken())) {
                        delivery.setAttempts(delivery.getAttempts()+1);
                        delivery.setNextAttemptAt(now+1000);
                        delivery.setState(delivery.getAttempts()<8 ? "PENDING" : "FAILED");
                    } else {
                        if(current!=null && Objects.equals(current.getToken(),device.getToken())) {
                            current.rejectToken(device.getToken());current.setEnabled(false);devices.save(current);
                        }
                        delivery.setState("FAILED");
                    }
                } else if(code==MessagingErrorCode.INVALID_ARGUMENT || code==MessagingErrorCode.SENDER_ID_MISMATCH) {
                    delivery.setState("FAILED");
                } else {
                    delivery.setAttempts(delivery.getAttempts()+1);
                    long delay=Math.min(300000L,5000L*(1L<<Math.min(delivery.getAttempts()-1,6)));
                    delivery.setNextAttemptAt(now+delay);
                    if(delivery.getAttempts()>=8) delivery.setState("FAILED");
                }
                log.warn("FCM delivery failed. deliveryId={}, app={}, platform={}, deviceId={}, code={}, state={}, eventId={}, reason={}",
                    delivery.getId(),delivery.getAppCode(),device.getPlatform(),device.getId(),
                    delivery.getLastError(),delivery.getState(),delivery.getEventId(),safeFailureReason(error,device,delivery));
            }
            deliveries.save(delivery);
            // Firebase acceptance is distinct from receipt/display on a phone.
            // Created broadcasts can have thousands of rows: log the time-
            // sensitive invitation/result path, without tokens or message text.
            if("SENT".equals(delivery.getState()) && isLottery(delivery) && !delivery.getEventId().contains("-created-")) {
                log.info("FCM lottery accepted. eventId={}, deliveryId={}, app={}, platform={}, deviceId={}",
                    delivery.getEventId(),delivery.getId(),delivery.getAppCode(),device.getPlatform(),device.getId());
            }
    }

    private static String iosSound(String appCode, Map<String,String> data) {
        // Mirror each Android app's channel mapping using bundled Apple sounds.
        if(Set.of("ELEHBER","ZAKAZ").contains(appCode) && ("pairing".equals(data.get("scope"))||"pairing".equals(data.get("channel")))) return "pairing_notify.caf";
        if (Set.of("ELEHBER","ZAKAZ").contains(appCode) && "won".equals(data.get("event")) &&
            ("lottery".equals(data.get("channel")) || "lottery".equals(data.get("scope")))) return "lottery_winner.caf";
        if (Set.of("ELEHBER","ZAKAZ").contains(appCode) && "lost".equals(data.get("event")) &&
            ("lottery".equals(data.get("channel")) || "lottery".equals(data.get("scope")))) return "lottery_result.caf";
        if (Set.of("ELEHBER","ZAKAZ").contains(appCode) && Set.of("live","created").contains(data.getOrDefault("event","")) &&
            ("lottery".equals(data.get("channel")) || "lottery".equals(data.get("scope")))) return "lottery_live.caf";
        if ("ELEHBER".equals(appCode)) {
            return Set.of("shop","lottery").contains(data.getOrDefault("channel","")) ? "chime.caf" : "notifysound.caf";
        }
        if (!"ZAKAZ".equals(appCode)) return "default";
        String channel=data.getOrDefault("channel",data.getOrDefault("scope","group"));
        return switch (channel) {
            case "group", "shop" -> "chime.caf";
            case "moto_chat" -> "notifysound.caf";
            default -> Set.of("mototaxi","mototaxi_chat").contains(data.getOrDefault("scope",""))
                ? "notifysound.caf" : "chime.caf";
        };
    }

    private String safeFailureReason(Exception error, PushDevice device, PushDelivery delivery) {
        // SDK messages distinguish invalid tokens, package names and payloads.
        // Never log request/response objects, tokens, credentials or message text.
        if (!(error instanceof FirebaseMessagingException)) return error.getClass().getSimpleName();
        String reason=Objects.toString(error.getMessage(),"No Firebase error description");
        var response=((FirebaseMessagingException)error).getHttpResponse();
        if(response!=null && response.getContent()!=null) {
            try {
                // The top-level message can be generic; APNs/field details
                // identify the actual rejection without dumping the response.
                var details=json.readTree(response.getContent()).path("error").path("details");
                for(var detail:details) {
                    String apnsReason=detail.path("apnsError").path("reason").asText("");
                    if(!apnsReason.isBlank()) reason+="; apnsReason="+apnsReason;
                    for(var violation:detail.path("fieldViolations")) {
                        reason+="; field="+violation.path("field").asText("")
                            +": "+violation.path("description").asText("");
                    }
                }
            } catch(Exception ignored) {
                // Keep the SDK description when structured details are absent.
            }
        }
        if(device.getToken()!=null && !device.getToken().isBlank()) {
            reason=reason.replace(device.getToken(),"[token]");
        }
        try {
            Map<String,String> payload=json.readValue(delivery.getPayload(),new TypeReference<Map<String,String>>(){});
            List<String> privateValues=new ArrayList<>(payload.values());
            privateValues.removeIf(value->value==null || value.isBlank());
            privateValues.sort(Comparator.comparingInt(String::length).reversed());
            for(String value:privateValues) {
                String escaped=json.writeValueAsString(value);
                reason=reason.replace(escaped.substring(1,escaped.length()-1),"[payload]")
                    .replace(value,"[payload]");
            }
        } catch(Exception ignored) {
            return "Firebase error description omitted: payload could not be redacted";
        }
        return reason.replaceAll("(?i)(Bearer|Basic)\\s+\\S+","[credential]")
            .replaceAll("[A-Za-z0-9_:/+=-]{40,}","[redacted]")
            .replaceAll("[\\p{Cntrl}\\u2028\\u2029]"," ")
            .codePoints().limit(600).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
    }
    @Scheduled(fixedDelay=86400000L,scheduler="pushTaskScheduler") @Transactional
    public void cleanup() { deliveries.deleteByExpiresAtLessThan(System.currentTimeMillis()-7*86400000L); }
}

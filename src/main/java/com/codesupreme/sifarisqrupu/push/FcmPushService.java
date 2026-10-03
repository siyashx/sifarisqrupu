package com.codesupreme.sifarisqrupu.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class FcmPushService {
    private static final Logger log=LoggerFactory.getLogger(FcmPushService.class);
    private final PushDeliveryPolicy policy;
    private final PushDeviceRepository devices;
    private final PushDeliveryRepository deliveries;
    private final UserRepository users;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String projectId;
    private FirebaseMessaging messaging;
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

    public FcmPushService(PushDeviceRepository devices, PushDeliveryRepository deliveries, UserRepository users,
        ObjectMapper json, PushDeliveryPolicy policy, @Value("${push.fcm.enabled:false}") boolean enabled,
        @Value("${push.fcm.project-id:bakuappcraft-notifications}") String projectId) {
        this.devices=devices; this.deliveries=deliveries; this.users=users; this.json=json;
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
        app(appCode);
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
        for(PushDevice device:devices.findByAppCodeAndUserIdInAndEnabledTrue(appCode,userIds)) {
            if(deliveries.existsByEventIdAndDeviceId(id,device.getId())) continue;
            PushDelivery item=new PushDelivery();
            item.setEventId(id); item.setDeviceId(device.getId()); item.setUserId(device.getUserId()); item.setAppCode(appCode);
            Map<String,String> targeted=new LinkedHashMap<>(values);
            targeted.put("recipientUserId",device.getUserId().toString());
            try { item.setPayload(json.writeValueAsString(targeted)); } catch(Exception e) {throw new IllegalArgumentException("Invalid target payload");} item.setExpiresAt(now+ttl*1000L); item.setNextAttemptAt(now); item.setState("PENDING");
            deliveries.save(item); queued++;
        }
        return queued;
    }
    // One worker in this deployment; durable rows survive Docker restarts.
    @Scheduled(fixedDelayString="${push.fcm.worker-delay-ms:1000}",scheduler="pushTaskScheduler")
    public void deliverDue() {
        if(!enabled) return;
        var batch=deliveries.findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc("PENDING",System.currentTimeMillis());
        CompletableFuture.allOf(batch.stream().map(delivery->CompletableFuture.runAsync(()->deliverOne(delivery),sendExecutor))
            .toArray(CompletableFuture[]::new)).join();
    }
    private void deliverOne(PushDelivery delivery) {
            long now=System.currentTimeMillis();
            PushDevice device=devices.findById(delivery.getDeviceId()).orElse(null);
            boolean userActive=users.findById(delivery.getUserId()).map(u->!Boolean.TRUE.equals(u.getIsDisable())).orElse(false);
            if(delivery.getExpiresAt()<=now || device==null || !device.isEnabled() || !userActive ||
                !Objects.equals(device.getUserId(),delivery.getUserId()) || !Objects.equals(device.getAppCode(),delivery.getAppCode())) {
                delivery.setState("EXPIRED"); deliveries.save(delivery); return;
            }
            try {
                Map<String,String> data=json.readValue(delivery.getPayload(),new TypeReference<Map<String,String>>(){});
                if(!policy.allows(data,delivery.getUserId())) {
                    delivery.setState("EXPIRED"); deliveries.save(delivery); return;
                }
                long remaining=delivery.getExpiresAt()-now;
                Message.Builder message=Message.builder().setToken(device.getToken()).putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(remaining).setRestrictedPackageName("ZAKAZ".equals(device.getAppCode()) ?
                            "com.bakuappcraft.zakazqrupu" : "com.bakuappcraft.elehber").build());
                if("ios".equals(device.getPlatform())) {
                    Aps.Builder aps=Aps.builder().setContentAvailable(true);
                    if(!data.getOrDefault("title", "").isBlank()) {
                        aps.setAlert(ApsAlert.builder().setTitle(data.get("title")).setBody(data.get("body")).build()).setSound("default");
                    }
                    message.setApnsConfig(ApnsConfig.builder().setAps(aps.build())
                        .putHeader("apns-expiration", Long.toString(delivery.getExpiresAt()/1000)).build());
                }
                messaging().send(message.build());
                delivery.setState("SENT"); delivery.setLastError(null);
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
                        if(current!=null && Objects.equals(current.getToken(),device.getToken())) {current.setEnabled(false);devices.save(current);}
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
                log.warn("FCM delivery failed. deliveryId={}, code={}, state={}",delivery.getId(),delivery.getLastError(),delivery.getState());
            }
            deliveries.save(delivery);
    }
    @Scheduled(fixedDelay=86400000L,scheduler="pushTaskScheduler") @Transactional
    public void cleanup() { deliveries.deleteByExpiresAtLessThan(System.currentTimeMillis()-7*86400000L); }
}

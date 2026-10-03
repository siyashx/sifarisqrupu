package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController @RequestMapping("/api/v5/push")
public class PushController {
    private final AdminVerificationPushService adminPush;
    private final PushAuth auth;
    private final PushDeviceRepository devices;
    private final FcmPushService fcm;
    private final PushAudienceService audience;
    private final OrderRepository orders;
    private final String bridgeKey;
    public PushController(PushAuth auth,PushDeviceRepository devices,FcmPushService fcm,PushAudienceService audience,
        OrderRepository orders,AdminVerificationPushService adminPush,@Value("${push.bridge-key:}") String bridgeKey) {
        this.adminPush=adminPush;this.auth=auth;this.devices=devices;this.fcm=fcm;this.audience=audience;this.orders=orders;this.bridgeKey=bridgeKey;
    }
    public record DeviceRequest(String appCode,String installationId,String token,String platform,boolean enabled) {}
    public record RevokeRequest(String appCode,String installationId) {}
    public record SendRequest(String appCode,String kind,String title,String body,List<Long> userIds,String requestId) {}
    public record OrderEventRequest(Long orderId,String event) {}
    public record BridgeRequest(String kind,String title,String body,String requestId) {}

    @GetMapping("/status")
    public Map<String,Object> status(@RequestHeader(value="Authorization",required=false) String authorization,@RequestParam String appCode) {
        User user=auth.requireUser(authorization);
        return Map.of("fcmEnabled",fcm.isEnabled(),"appCode",FcmPushService.app(appCode),
            "registeredDevices",devices.findByAppCodeAndUserIdInAndEnabledTrue(appCode,List.of(user.getId())).size());
    }
    @PutMapping("/devices") @Transactional
    public synchronized Map<String,Object> register(@RequestHeader(value="Authorization",required=false) String authorization,
        @RequestBody DeviceRequest request) {
        User user=auth.requireUser(authorization);
        String app=FcmPushService.app(request.appCode());
        if(request.installationId()==null || !request.installationId().matches("[a-zA-Z0-9-]{16,64}") ||
            request.token()==null || request.token().length()<20 || request.token().length()>4096 ||
            request.platform()==null || !Set.of("android","ios").contains(request.platform())) throw new IllegalArgumentException("Invalid device registration");
        PushDevice device=devices.findByAppCodeAndInstallationId(app,request.installationId()).orElseGet(PushDevice::new);
        for(PushDevice other:devices.findByAppCodeAndToken(app,request.token())) {
            if(!Objects.equals(other.getId(),device.getId())) {other.setEnabled(false);devices.save(other);}
        }
        device.setAppCode(app);device.setInstallationId(request.installationId());device.setUserId(user.getId());
        device.setPlatform(request.platform());device.setToken(request.token());device.setEnabled(request.enabled());
        device.setUpdatedAt(System.currentTimeMillis());devices.save(device);
        return Map.of("registered",true,"fcmEnabled",fcm.isEnabled());
    }
    @PostMapping("/devices/revoke") @Transactional
    public synchronized Map<String,Boolean> revoke(@RequestHeader(value="Authorization",required=false) String authorization,
        @RequestBody RevokeRequest request) {
        User user=auth.requireUser(authorization);
        devices.findByAppCodeAndInstallationId(FcmPushService.app(request.appCode()),request.installationId()).ifPresent(d->{
            if(Objects.equals(d.getUserId(),user.getId())) {d.setEnabled(false);devices.save(d);}
        });
        return Map.of("revoked",true);
    }
    @PostMapping("/send")
    public Map<String,Object> send(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody SendRequest request) {
        User user=auth.requireUser(authorization); requireEnabled();
        String app=FcmPushService.app(request.appCode());
        String kind=request.kind();
        if(kind==null || !Set.of("group","shop","moto_chat","notice").contains(kind) ||
            ("ELEHBER".equals(app) && !"moto_chat".equals(kind) && !"notice".equals(kind))) throw new IllegalArgumentException("Invalid notification kind");
        if("notice".equals(kind) && (request.userIds()==null || request.userIds().isEmpty())) throw new IllegalArgumentException("notice recipients required");
        String title=limit(request.title(),120), body=limit(request.body(),550);
        if(body.isBlank()) throw new IllegalArgumentException("body is required");
        String id=requestId(request.requestId(),"user-"+user.getId());
        int count=fcm.enqueue(app,audience.select(app,kind,user.getId(),request.userIds(),false),title,body,
            PushAudienceService.payload(app,kind),300,id+"-"+app);
        return Map.of("queued",count);
    }
    @PostMapping("/chat")
    public Map<String,Object> chat(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody SendRequest request) {
        User user=auth.requireUser(authorization); requireEnabled();
        String sourceApp=FcmPushService.app(request.appCode());
        String body=limit(request.body(),550);
        if(body.isBlank()) throw new IllegalArgumentException("body is required");
        String id=requestId(request.requestId(),"chat-"+user.getId());
        int count=0;
        for(String app:List.of("ZAKAZ","ELEHBER")) {
            count+=fcm.enqueue(app,audience.select(app,"moto_chat",user.getId(),null,!"ZAKAZ".equals(sourceApp)),
                "🛵 Yeni Moto Taksi sifarişi!",body,PushAudienceService.payload(app,"moto_chat"),300,id+"-"+app);
        }
        return Map.of("queued",count);
    }
    @PostMapping("/bridge")
    public Map<String,Object> bridge(@RequestHeader(value="X-Push-Bridge-Key",required=false) String supplied,@RequestBody BridgeRequest request) {
        if(bridgeKey.isBlank() || supplied==null || !MessageDigest.isEqual(bridgeKey.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bridge authentication required");
        requireEnabled();
        if(request.kind()==null || !Set.of("group","moto_chat").contains(request.kind())) throw new IllegalArgumentException("Invalid bridge notification kind");
        String id=requestId(request.requestId(),"bridge"); int count=0;
        for(String app:("moto_chat".equals(request.kind()) ? List.of("ZAKAZ","ELEHBER") : List.of("ZAKAZ"))) {
            count+=fcm.enqueue(app,audience.select(app,request.kind(),null,null,true),limit(request.title(),120),limit(request.body(),550),
                PushAudienceService.payload(app,request.kind()),300,id+"-"+app);
        }
        return Map.of("queued",count);
    }
    @PostMapping("/verification")
    public Map<String,Object> verification(@RequestHeader(value="Authorization",required=false) String authorization) {
        User user=auth.requireUser(authorization);
        if(user.getIdentifyPhoto()==null || user.getIdentifyPhoto().size()<3) throw new IllegalArgumentException("Courier documents required");
        return Map.of("accepted",adminPush.notifyAdmin(user.getId()));
    }
    @PostMapping("/order-event") @Transactional
    public Map<String,Object> orderEvent(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody OrderEventRequest request) {
        User user=auth.requireUser(authorization);requireEnabled();
        if(request.orderId()==null || request.event()==null) throw new IllegalArgumentException("Order and event required");
        Order order=orders.findById(request.orderId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String event=request.event(); String status=order.getStatus();
        boolean delivery="delivery".equalsIgnoreCase(order.getOrderType());
        boolean owner=Objects.equals(user.getId(),order.getCustomerId());
        boolean courier=Objects.equals(user.getId(),order.getCourierId());
        boolean formerCourier=order.getCancelledCourierIds()!=null && order.getCancelledCourierIds().contains(user.getId());
        // Courier offers and broadcast stop commands are emitted by server dispatch, never by mobile clients.
        if(Set.of("new_order","order_unavailable").contains(event)) return Map.of("queued",0,"serverManaged",true);
        Long recipient; String body;
        switch(event) {
            case "customer_cancelled":
                if(!owner || !"cancelled".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                // Dispatch has already notified offered/assigned couriers on the status transition.
                return Map.of("queued",0,"serverManaged",true);
            case "accepted":
                if(!courier || !"to_customer".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                body=delivery ? "Sifarişiniz kuryer tərəfindən qəbul edildi. Kuryer bağlamanı götürməyə gəlir." : "Sifarişiniz sürücü tərəfindən qəbul edildi. Sürücü sizə tərəf yoldadır."; break;
            case "courierCancelled":
                if(!formerCourier || !"no_courier".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                body=delivery ? "Kuryer bağlama sifarişini ləğv etdi. Sizin üçün yenidən kuryer axtarılır." : "Sifarişinizi qəbul etmiş sürücü sifarişi ləğv etdi. Sizin üçün yenidən sürücü axtarılır."; break;
            case "pickedUp":
                if(!courier || !"on_the_way".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                body=delivery ? "Kuryer artıq bağlamanızı götürdü və ünvanınıza yoldadır." : "Sürücü artıq sizi götürdü və təyinat ünvanına yoldadır."; break;
            case "completed":
                if(!courier || !"completed".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                body=delivery ? "Bağlama sifarişiniz uğurla çatdırıldı. Xidmətimizdən istifadə etdiyiniz üçün təşəkkürlər!" : "Sifarişiniz tamamlandı. MotoTaksi xidmətindən istifadə etdiyiniz üçün təşəkkürlər!"; break;
            default: throw new IllegalArgumentException("Invalid order event");
        }
        recipient=order.getCustomerId();
        int count=recipient==null ? 0 : fcm.enqueue("ELEHBER",List.of(recipient),"MotoTaksi",body,
            Map.of("scope","mototaxi","event",event,"status",status,"orderId",order.getId().toString(),"channel","moto_chat"),300,
            "order-"+order.getId()+"-"+event+"-"+user.getId());
        return Map.of("queued",count);
    }
    private void requireEnabled() {if(!fcm.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"FCM disabled");}
    static String limit(String value,int max) { return value==null ? "" : value.strip().codePoints().limit(max).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString(); }
    static String requestId(String value,String prefix) {
        if(value==null || !value.matches("[a-zA-Z0-9-]{1,64}")) return prefix+"-"+UUID.randomUUID();
        return prefix+"-"+value;
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(IllegalArgumentException error) {return Map.of("error","Invalid push request");}
}

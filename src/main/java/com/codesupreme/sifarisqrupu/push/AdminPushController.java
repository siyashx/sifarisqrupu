package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dao.chat_group.ChatGroupRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/api/v5/push/admin")
public class AdminPushController {
    private final AdminPushAuth auth;
    private final PushDeviceRepository devices;
    private final FcmPushService fcm;
    private final UserRepository users;
    private final ChatGroupRepository groups;
    private final PushAudienceService audience;
    public AdminPushController(AdminPushAuth auth,PushDeviceRepository devices,FcmPushService fcm,UserRepository users,
        ChatGroupRepository groups,PushAudienceService audience) {
        this.auth=auth;this.devices=devices;this.fcm=fcm;this.users=users;this.groups=groups;this.audience=audience;
    }
    public record DeviceRequest(String installationId,String token,boolean enabled) {}
    public record RevokeRequest(String installationId) {}
    public record SendRequest(String kind,List<Long> userIds,String title,String body,Long groupId,String screen,String requestId) {}
    @GetMapping("/status")
    public Map<String,Object> status(@RequestHeader(value="Authorization",required=false) String authorization) {
        var admin=auth.requireAdmin(authorization);
        return Map.of("adminId",admin.getId(),"fcmEnabled",fcm.isEnabled(),"registeredDevices",
            devices.findByAppCodeAndUserIdInAndEnabledTrue("ADMIN",List.of(admin.getId())).size());
    }
    @PutMapping("/devices") @Transactional
    public synchronized Map<String,Object> register(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody DeviceRequest r) {
        var admin=auth.requireAdmin(authorization);
        if(r.installationId()==null || !r.installationId().matches("[a-zA-Z0-9-]{16,64}") || r.token()==null || r.token().length()<20 || r.token().length()>4096) throw new IllegalArgumentException();
        var device=devices.findByAppCodeAndInstallationId("ADMIN",r.installationId()).orElseGet(PushDevice::new);
        for(var other:devices.findByAppCodeAndToken("ADMIN",r.token())) if(!Objects.equals(other.getId(),device.getId())) {other.setEnabled(false);devices.save(other);}
        device.setAppCode("ADMIN");device.setUserId(admin.getId());device.setPlatform("android");device.setInstallationId(r.installationId());
        device.setToken(r.token());device.setEnabled(r.enabled());device.setUpdatedAt(System.currentTimeMillis());devices.save(device);
        return Map.of("registered",true,"fcmEnabled",fcm.isEnabled(),"adminId",admin.getId());
    }
    @PostMapping("/devices/revoke") @Transactional
    public Map<String,Boolean> revoke(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody RevokeRequest r) {
        var admin=auth.requireAdmin(authorization);
        devices.findByAppCodeAndInstallationId("ADMIN",r.installationId()).ifPresent(d->{if(Objects.equals(d.getUserId(),admin.getId())) {d.setEnabled(false);devices.save(d);}});
        return Map.of("revoked",true);
    }
    @PostMapping("/send")
    public Map<String,Integer> send(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody SendRequest r) {
        var admin=auth.requireAdmin(authorization);
        if(!fcm.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"FCM disabled");
        if(r.kind()==null || !Set.of("group","shop","community","notice","verification_result").contains(r.kind()) ||
            r.userIds()==null || r.userIds().isEmpty() || r.userIds().size()>10000) throw new IllegalArgumentException();
        String title=PushController.limit(r.title(),120),body=PushController.limit(r.body(),550);
        if(body.isBlank()) throw new IllegalArgumentException();
        String event=PushController.requestId(r.requestId(),"admin-"+admin.getId());
        List<Long> ids=r.userIds().stream().filter(Objects::nonNull).distinct().toList();
        int queued=0;
        if("verification_result".equals(r.kind()) || "notice".equals(r.kind())) {
            var eligible=users.findAllById(ids).stream().filter(u->!Boolean.TRUE.equals(u.getIsDisable()))
                .filter(u->!"verification_result".equals(r.kind()) || Set.of("accept","decline").contains(Objects.toString(u.getCourierStatus(),"")))
                .map(u->u.getId()).toList();
            for(String app:List.of("ZAKAZ","ELEHBER")) queued+=fcm.enqueue(app,eligible,title,body,
                PushAudienceService.payload(app,"notice"),3600,event+"-"+app);
        } else {
            String kind="shop".equals(r.kind()) ? "shop":"group";
            var eligible=audience.select("ZAKAZ",kind,null,ids,true);
            Map<String,Object> payload=PushAudienceService.payload("ZAKAZ",kind);
            if("community".equals(r.kind())) {
                var group=groups.findById(Objects.requireNonNull(r.groupId())).orElseThrow(IllegalArgumentException::new);
                var joined=group.getJoinedUserIds();var muted=group.getMutedUserIds();
                eligible=users.findAllById(ids).stream().filter(u->!Boolean.TRUE.equals(u.getIsDisable()))
                    .map(u->u.getId()).filter(id->joined!=null && joined.contains(id) && (muted==null || !muted.contains(id))).toList();
                payload=Map.of("screen",PushController.limit(r.screen(),80),"groupId",r.groupId(),"channel","shop");
            }
            queued=fcm.enqueue("ZAKAZ",eligible,title,body,payload,300,event+"-ZAKAZ");
        }
        return Map.of("queued",queued);
    }
    @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid() {return Map.of("error","Invalid admin push request");}
}

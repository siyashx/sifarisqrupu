package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Service
public class AdminVerificationPushService {
    private static final Logger log=LoggerFactory.getLogger(AdminVerificationPushService.class);
    private final FcmPushService fcm;
    private final AdminRepository admins;
    private final UserRepository users;
    private final List<Long> recipients;
    public AdminVerificationPushService(FcmPushService fcm,AdminRepository admins,UserRepository users,
        @Value("${push.admin.recipient-ids:1}") String ids) {
        this.fcm=fcm;this.admins=admins;this.users=users;
        this.recipients=Arrays.stream(ids.split(",")).map(String::trim).filter(s->!s.isEmpty()).map(Long::parseLong).toList();
    }
    public boolean notifyAdmin(Long courierId) {
        if(!fcm.isEnabled()) return false;
        var user=users.findById(courierId).orElse(null);
        if(user==null || Boolean.TRUE.equals(user.getIsDisable()) || !"active".equals(user.getCourierStatus()) ||
            user.getIdentifyPhoto()==null || user.getIdentifyPhoto().size()<3) return false;
        List<Long> targets=admins.findAllById(recipients).stream()
            .filter(a->!Boolean.TRUE.equals(a.getIsDisable()) && !Boolean.TRUE.equals(a.getIsMutedNotifications()))
            .map(a->a.getId()).toList();
        try {
            // Both document-save and the existing mobile /verification call use the same key.
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n",user.getIdentifyPhoto()).getBytes(StandardCharsets.UTF_8))).substring(0,32);
            return fcm.enqueue("ADMIN",targets,"🛵 Yeni Kuryer İstəyi",
                "Kuryer qeydiyyat üçün sənədlərini sorğu üçün sizə göndərdi.",
                Map.of("scope","courier_verification","courierId",courierId,"screen","UserDetails","channel","admin_requests"),86400,
                "verification-"+courierId+"-"+fingerprint)>0;
        } catch(Exception error) {
            log.warn("Admin verification enqueue failed; courierId={}, type={}",courierId,error.getClass().getSimpleName());
            return false;
        }
    }
}

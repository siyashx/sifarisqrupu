package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Admin device registration belongs to the admin client, which is not included in this migration. */
@Service
public class AdminVerificationPushService {
    private static final Logger log=LoggerFactory.getLogger(AdminVerificationPushService.class);
    private final FcmPushService fcm;
    private final AdminRepository admins;
    private final List<Long> fcmUsers;
    private final String fcmApp;
    private final String legacyApp;
    private final String legacyKey;
    private final RestClient client;
    public AdminVerificationPushService(FcmPushService fcm, AdminRepository admins,
        @Value("${push.admin.user-ids:}") String userIds,
        @Value("${push.admin.app-code:ELEHBER}") String fcmApp,
        @Value("${mototaxi.onesignal.app-id:}") String legacyApp,
        @Value("${mototaxi.onesignal.rest-api-key:}") String legacyKey) {
        this.fcm=fcm;this.admins=admins;this.fcmApp=FcmPushService.app(fcmApp);
        this.fcmUsers=Arrays.stream(userIds.split(",")).map(String::trim).filter(s->!s.isEmpty()).map(Long::parseLong).toList();
        this.legacyApp=legacyApp;this.legacyKey=legacyKey;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(10000);
        client=RestClient.builder().requestFactory(factory).baseUrl("https://api.onesignal.com").build();
    }
    public boolean notifyAdmin(Long courierId) {
        String title="🛵 Yeni Kuryer İstəyi";
        String body="Kuryer qeydiyyat üçün sənədlərini sorğu üçün sizə göndərdi.";
        if(fcm.isEnabled() && !fcmUsers.isEmpty()) {
            return fcm.enqueue(fcmApp,fcmUsers,title,body,Map.of("scope","courier_verification","courierId",courierId,"channel","group"),3600,
                "verification-"+courierId+"-"+(System.currentTimeMillis()/300000))>0;
        }
        // Preserve existing admin delivery on the server until the separate admin client is migrated.
        var admin=admins.findById(1L).orElse(null);
        if(admin==null || admin.getOneSignal()==null || admin.getOneSignal().isBlank() || legacyKey.isBlank() || legacyApp.isBlank()) {
            log.warn("Admin verification push pending configuration; courierId={}",courierId);
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String,Object> result=client.post().uri("/notifications").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization","Key "+legacyKey)
                .body(Map.of("app_id",legacyApp,"include_subscription_ids",List.of(admin.getOneSignal()),"target_channel","push",
                    "headings",Map.of("en",title),"contents",Map.of("en",body)))
                .retrieve().body(Map.class);
            return result!=null && result.get("id")!=null && !result.get("id").toString().isBlank();
        } catch(Exception error) {
            log.warn("Admin verification legacy push unavailable; courierId={}",courierId);
            return false;
        }
    }
}

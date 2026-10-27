package com.codesupreme.sifarisqrupu.dashboard;

import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.advertising.AdRequestRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v5/push/admin/dashboard-counts")
public class AdminDashboardController {
    private final AdminPushAuth auth;
    private final UserRepository users;
    private final AdRequestRepository ads;
    public AdminDashboardController(AdminPushAuth auth, UserRepository users, AdRequestRepository ads) {
        this.auth=auth;this.users=users;this.ads=ads;
    }
    @GetMapping @Transactional(readOnly=true)
    public Map<String,Long> counts(@RequestHeader(value="Authorization", required=false) String key) {
        auth.requireAdmin(key);
        return Map.of("couriers",users.countByCourierStatus("active"),"chatAds",ads.countByKindAndStatus("CHAT","PENDING"),"listingAds",ads.countByKindAndStatus("SHOP","PENDING"));
    }
}

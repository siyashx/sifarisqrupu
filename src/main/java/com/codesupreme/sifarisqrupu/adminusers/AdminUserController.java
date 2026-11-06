package com.codesupreme.sifarisqrupu.adminusers;

import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/push/admin/users")
public class AdminUserController {
    private final AdminPushAuth auth;
    private final AdminUserService service;
    public AdminUserController(AdminPushAuth auth, AdminUserService service) {
        this.auth=auth; this.service=service;
    }
    @GetMapping
    public AdminUserService.Page list(
        @RequestHeader(value="Authorization",required=false) String key,
        @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="all") String type,
        @RequestParam(defaultValue="none") String sort,
        @RequestParam(defaultValue="all") String status,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="40") int size
    ) {
        auth.requireAdmin(key);
        return service.list(q,type,sort,status,page,size);
    }
}

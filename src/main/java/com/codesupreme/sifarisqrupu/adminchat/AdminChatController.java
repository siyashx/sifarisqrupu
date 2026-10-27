package com.codesupreme.sifarisqrupu.adminchat;

import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/push/admin/chat-feed")
public class AdminChatController {
    private final AdminPushAuth auth;
    private final AdminChatService service;
    public AdminChatController(AdminPushAuth auth, AdminChatService service) {
        this.auth = auth; this.service = service;
    }
    @GetMapping
    public Map<String, Object> feed(@RequestHeader(value="Authorization", required=false) String key,
                                   @RequestParam String group) {
        auth.requireAdmin(key);
        return service.feed(group);
    }
}

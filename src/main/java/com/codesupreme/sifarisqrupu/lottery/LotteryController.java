package com.codesupreme.sifarisqrupu.lottery;
import com.codesupreme.sifarisqrupu.push.PushAuth;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v5/lotteries")
public class LotteryController {
    private final PushAuth auth;private final LotteryService service;
    public LotteryController(PushAuth auth,LotteryService service){this.auth=auth;this.service=service;}
    @GetMapping public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String key){return Map.of("items",service.list(auth.requireUser(key).getId(),false),"serverNow",System.currentTimeMillis());}
    @GetMapping("/{id}") public LotteryService.View detail(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id){return service.detail(id,auth.requireUser(key).getId(),false);}
}

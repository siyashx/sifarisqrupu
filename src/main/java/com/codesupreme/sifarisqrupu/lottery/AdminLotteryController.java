package com.codesupreme.sifarisqrupu.lottery;
import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v5/push/admin/lotteries")
public class AdminLotteryController {
    private final AdminPushAuth auth;private final LotteryService service;
    public AdminLotteryController(AdminPushAuth auth,LotteryService service){this.auth=auth;this.service=service;}
    @GetMapping public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String key){auth.requireAdmin(key);return Map.of("items",service.list(null,true),"serverNow",System.currentTimeMillis());}
    @GetMapping("/users") public LotteryService.PageView<LotteryService.Person> people(@RequestHeader(value="Authorization",required=false) String key,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page){auth.requireAdmin(key);return service.people(q,page);}
    @PostMapping public LotteryService.View create(@RequestHeader(value="Authorization",required=false) String key,@RequestBody LotteryService.Form form){return service.create(form,auth.requireAdmin(key).getId());}
    @GetMapping("/{id}") public LotteryService.View detail(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id){auth.requireAdmin(key);return service.detail(id,null,true);}
    @PutMapping("/{id}") public LotteryService.View edit(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id,@RequestBody LotteryService.Form form){auth.requireAdmin(key);return service.edit(id,form);}
    @PostMapping("/{id}/cancel") public LotteryService.View cancel(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id){auth.requireAdmin(key);return service.cancel(id);}
    @GetMapping("/{id}/tickets") public LotteryService.PageView<LotteryService.TicketView> tickets(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id,@RequestParam(defaultValue="0") int page){auth.requireAdmin(key);return service.ticketPage(id,page);}
    @PostMapping("/{id}/tickets") public Map<String,Object> issue(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id,@RequestBody LotteryService.Issue request){return Map.of("codes",service.issue(id,request,auth.requireAdmin(key).getId()));}
    @PostMapping("/{id}/live") public LotteryService.View open(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id,@RequestBody LotteryService.Open request){auth.requireAdmin(key);return service.open(id,request);}
    @PostMapping("/{id}/spin") public LotteryService.View spin(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id){auth.requireAdmin(key);return service.spin(id);}
    @PostMapping("/{id}/notify") public Map<String,Integer> notify(@RequestHeader(value="Authorization",required=false) String key,@PathVariable Long id){auth.requireAdmin(key);return Map.of("queued",service.notifyParticipants(id));}
}

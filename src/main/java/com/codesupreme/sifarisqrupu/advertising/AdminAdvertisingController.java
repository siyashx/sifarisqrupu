package com.codesupreme.sifarisqrupu.advertising;
import com.codesupreme.sifarisqrupu.push.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v5/push/admin/advertising") public class AdminAdvertisingController {
 private final AdminPushAuth auth;private final AdvertisingService service;public AdminAdvertisingController(AdminPushAuth a,AdvertisingService s){auth=a;service=s;}
 @GetMapping public Map<String,Object> all(@RequestHeader("Authorization")String key){auth.requireAdmin(key);return Map.of("items",service.all(),"plans",service.plans(true));}
 @PostMapping("/plans") public AdPlan create(@RequestHeader("Authorization")String key,@RequestBody AdvertisingService.PlanForm f){auth.requireAdmin(key);return service.savePlan(null,f);}
 @PutMapping("/plans/{id}") public AdPlan edit(@RequestHeader("Authorization")String key,@PathVariable Long id,@RequestBody AdvertisingService.PlanForm f){auth.requireAdmin(key);return service.savePlan(id,f);}
 @DeleteMapping("/plans/{id}") public Map<String,Boolean> delete(@RequestHeader("Authorization")String key,@PathVariable Long id){auth.requireAdmin(key);service.deletePlan(id);return Map.of("deleted",true);}
 @PostMapping("/requests/{id}/{action}") public Map<String,Object> moderate(@RequestHeader("Authorization")String key,@PathVariable Long id,@PathVariable String action){auth.requireAdmin(key);return service.moderate(id,action);}
 @PostMapping("/promote") public Map<String,Object> promote(@RequestHeader("Authorization")String key,@RequestBody Map<String,Object> body){auth.requireAdmin(key);var request=service.request(((Number)body.get("userId")).longValue(),new AdvertisingService.RequestForm("SHOP",null,((Number)body.get("planId")).longValue(),((Number)body.get("listingId")).longValue(),null,null,null,null,null,((Number)body.get("requestedStart")).longValue(),Objects.toString(body.get("requestId")),null));return service.moderate(((Number)request.get("id")).longValue(),"approve");}
}

package com.codesupreme.sifarisqrupu.advertising;
import com.codesupreme.sifarisqrupu.push.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v5/advertising") public class AdvertisingController {
 private final PushAuth auth;private final AdvertisingService service;public AdvertisingController(PushAuth a,AdvertisingService s){auth=a;service=s;}
 @GetMapping("/feed") public Map<String,Object> feed(@RequestHeader("Authorization")String key,@RequestParam(defaultValue="SUSHI")String channel){auth.requireUser(key);return service.feed(channel);}
 @GetMapping("/mine") public Map<String,Object> mine(@RequestHeader("Authorization")String key,@RequestParam(defaultValue="SUSHI")String channel){return service.mine(auth.requireUser(key).getId(),channel);}
 @GetMapping("/promotion/{listing}") public Map<String,Object> promotion(@RequestHeader("Authorization")String key,@PathVariable Long listing){return service.promotion(auth.requireUser(key).getId(),listing);}
 @PostMapping("/visit") public Map<String,Object> visit(@RequestHeader("Authorization")String key,@RequestParam(defaultValue="SHOP")String section){return service.visit(auth.requireUser(key).getId(),section);}
 @PostMapping("/requests") public Map<String,Object> request(@RequestHeader("Authorization")String key,@RequestBody AdvertisingService.RequestForm f){return service.request(auth.requireUser(key).getId(),f);}
}

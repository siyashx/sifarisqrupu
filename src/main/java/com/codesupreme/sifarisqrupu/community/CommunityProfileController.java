package com.codesupreme.sifarisqrupu.community;

import com.codesupreme.sifarisqrupu.push.PushAuth;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/community")
public class CommunityProfileController {

  private final PushAuth auth;

  public CommunityProfileController(PushAuth auth) {
    this.auth = auth;
  }

  @GetMapping("/me")
  public Map<String, Object> me(@RequestHeader("Authorization") String key) {
    var u = auth.requireUser(key);
    return Map.of(
      "id",
      u.getId(),
      "name",
      Objects.toString(u.getName(), ""),
      "phoneNumber",
      Objects.toString(u.getPhoneNumber(), "")
    );
  }
}

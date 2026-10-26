package com.codesupreme.sifarisqrupu.pairing;

import com.codesupreme.sifarisqrupu.push.PushAuth;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/pairings")
public class PairingController {

  private final PushAuth auth;
  private final PairingService service;

  public PairingController(PushAuth auth, PairingService service) {
    this.auth = auth;
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
    @RequestHeader(value = "Authorization", required = false) String key
  ) {
    return Map.of(
      "items",
      service.list(auth.requireUser(key).getId()),
      "serverNow",
      System.currentTimeMillis()
    );
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id
  ) {
    return service.detail(id, auth.requireUser(key).getId());
  }

  @PostMapping("/{id}/rounds/{round}/games/{game}/result")
  public Map<String, Object> result(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @PathVariable int round,
    @PathVariable int game,
    @RequestBody PairingService.Result result
  ) {
    return service.result(
      id,
      round,
      game,
      auth.requireUser(key).getId(),
      result,
      false
    );
  }
}

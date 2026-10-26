package com.codesupreme.sifarisqrupu.pairing;

import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v5/push/admin/pairings")
public class AdminPairingController {

  private final AdminPushAuth auth;
  private final PairingService service;

  public AdminPairingController(AdminPushAuth auth, PairingService service) {
    this.auth = auth;
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> list(
    @RequestHeader(value = "Authorization", required = false) String key
  ) {
    auth.requireAdmin(key);
    return Map.of(
      "items",
      service.list(null),
      "serverNow",
      System.currentTimeMillis()
    );
  }

  @GetMapping("/users")
  public Map<String, Object> users(
    @RequestHeader(value = "Authorization", required = false) String key,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page
  ) {
    auth.requireAdmin(key);
    return service.search(q, page);
  }

  @PostMapping
  public Map<String, Object> create(
    @RequestHeader(value = "Authorization", required = false) String key,
    @RequestBody PairingService.Form form
  ) {
    return service.create(form, auth.requireAdmin(key).getId());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id
  ) {
    auth.requireAdmin(key);
    return service.detail(id, null);
  }

  @PutMapping("/{id}")
  public Map<String, Object> edit(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @RequestBody PairingService.Form form
  ) {
    auth.requireAdmin(key);
    return service.edit(id, form);
  }

  @PostMapping("/{id}/start")
  public Map<String, Object> start(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @RequestBody PairingService.Start form
  ) {
    auth.requireAdmin(key);
    return service.start(id, form);
  }

  @PostMapping("/{id}/notify")
  public Map<String, Object> notify(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @RequestBody Map<String, String> form
  ) {
    auth.requireAdmin(key);
    return service.notify(
      id,
      form.getOrDefault("audience", "none"),
      form.get("requestId")
    );
  }

  @PostMapping("/{id}/status")
  public Map<String, Object> status(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @RequestBody Map<String, String> form
  ) {
    auth.requireAdmin(key);
    return service.status(id, form.get("status"));
  }

  @PostMapping("/{id}/delete")
  public Map<String, Boolean> delete(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id
  ) {
    auth.requireAdmin(key);
    service.delete(id);
    return Map.of("deleted", true);
  }

  @PostMapping("/{id}/rounds/{round}/games/{game}/result")
  public Map<String, Object> result(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @PathVariable int round,
    @PathVariable int game,
    @RequestBody PairingService.Result result
  ) {
    auth.requireAdmin(key);
    return service.result(id, round, game, null, result, true);
  }

  @PostMapping("/{id}/rounds/{round}/games/{game}/reset")
  public Map<String, Object> reset(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable Long id,
    @PathVariable int round,
    @PathVariable int game
  ) {
    auth.requireAdmin(key);
    return service.reset(id, round, game);
  }
}

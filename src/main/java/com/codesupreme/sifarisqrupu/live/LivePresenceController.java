package com.codesupreme.sifarisqrupu.live;

import com.codesupreme.sifarisqrupu.push.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LivePresenceController {

  private final PushAuth users;
  private final AdminPushAuth admins;
  private final LivePresenceService presence;

  public LivePresenceController(
    PushAuth users,
    AdminPushAuth admins,
    LivePresenceService presence
  ) {
    this.users = users;
    this.admins = admins;
    this.presence = presence;
  }

  public record Request(String sessionId, boolean leave) {}

  @PostMapping("/api/v5/live/{kind}/{id}/viewers")
  public Map<String, Object> beat(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable String kind,
    @PathVariable Long id,
    @RequestBody Request r
  ) {
    return presence.heartbeat(
      kind,
      id,
      r.sessionId(),
      users.requireUser(key).getId(),
      false,
      r.leave()
    );
  }

  @GetMapping("/api/v5/live/{kind}/{id}/viewers")
  public Map<String, Object> list(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable String kind,
    @PathVariable Long id
  ) {
    users.requireUser(key);
    return presence.list(kind, id);
  }

  @PostMapping("/api/v5/push/admin/live/{kind}/{id}/viewers")
  public Map<String, Object> adminBeat(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable String kind,
    @PathVariable Long id,
    @RequestBody Request r
  ) {
    return presence.heartbeat(
      kind,
      id,
      r.sessionId(),
      admins.requireAdmin(key).getId(),
      true,
      r.leave()
    );
  }

  @GetMapping("/api/v5/push/admin/live/{kind}/{id}/viewers")
  public Map<String, Object> adminList(
    @RequestHeader(value = "Authorization", required = false) String key,
    @PathVariable String kind,
    @PathVariable Long id
  ) {
    admins.requireAdmin(key);
    return presence.list(kind, id);
  }
}

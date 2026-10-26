package com.codesupreme.sifarisqrupu.live;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.LotteryRepository;
import com.codesupreme.sifarisqrupu.pairing.PairingRepository;
import com.codesupreme.sifarisqrupu.pairing.PairingService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LivePresenceService {

  private record Key(String kind, Long id) {}

  private record Viewer(
    Long id,
    String name,
    String avatarUrl,
    boolean admin
  ) {}

  private record Session(Viewer viewer, long expires) {}

  private final Map<Key, Map<String, Session>> rooms =
    new ConcurrentHashMap<>();
  private final UserRepository users;
  private final LotteryRepository lotteries;
  private final PairingRepository pairings;

  public LivePresenceService(
    UserRepository users,
    LotteryRepository lotteries,
    PairingRepository pairings
  ) {
    this.users = users;
    this.lotteries = lotteries;
    this.pairings = pairings;
  }

  private Key key(String kind, Long id) {
    boolean exists = "lottery".equals(kind)
      ? lotteries
          .findById(id)
          .filter(l -> !l.isDeleted())
          .isPresent()
      : "pairing".equals(kind) &&
        pairings
          .findById(id)
          .filter(p -> !p.isDeleted())
          .isPresent();
    if (!exists) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Canlı səhifə tapılmadı."
    );
    return new Key(kind, id);
  }

  public synchronized Map<String, Object> heartbeat(
    String kind,
    Long id,
    String session,
    Long userId,
    boolean admin,
    boolean leave
  ) {
    if (
      session == null || !session.matches("[a-zA-Z0-9-]{16,64}")
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Yanlış sessiya."
    );
    var key = key(kind, id);
    var room = rooms.computeIfAbsent(key, k -> new HashMap<>());
    String identity = (admin ? "a:" : "u:") + userId + ":" + session;
    if (leave) room.remove(identity);
    else {
      Viewer viewer;
      if (admin) viewer = new Viewer(userId, "Admin", null, true);
      else {
        var user = users.findById(userId).orElseThrow();
        viewer = new Viewer(
          userId,
          Objects.toString(user.getName(), "İstifadəçi"),
          PairingService.avatar(user),
          false
        );
      }
      room.put(
        identity,
        new Session(viewer, System.currentTimeMillis() + 30000)
      );
    }
    return snapshot(key);
  }

  public synchronized Map<String, Object> list(String kind, Long id) {
    return snapshot(key(kind, id));
  }

  private Map<String, Object> snapshot(Key key) {
    long now = System.currentTimeMillis();
    rooms
      .values()
      .forEach(room -> room.values().removeIf(s -> s.expires < now));
    rooms.entrySet().removeIf(e -> e.getValue().isEmpty());
    var viewers = new LinkedHashMap<String, Viewer>();
    for (var session : rooms.getOrDefault(key, Map.of()).values()) {
      var v = session.viewer;
      viewers.put((v.admin ? "a:" : "u:") + v.id, v);
    }
    var list = viewers
      .values()
      .stream()
      .sorted(Comparator.comparing(Viewer::name).thenComparing(Viewer::id))
      .toList();
    return Map.of("count", list.size(), "items", list, "serverNow", now);
  }
}

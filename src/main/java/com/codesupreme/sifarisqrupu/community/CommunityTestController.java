package com.codesupreme.sifarisqrupu.community;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.*;
import com.codesupreme.sifarisqrupu.pairing.*;
import com.codesupreme.sifarisqrupu.push.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only controls: every mutation verifies that the target is an owned test record. */
@RestController
@RequestMapping("/api/v5/push/admin/community-test")
public class CommunityTestController {

  private final AdminPushAuth auth;
  private final PairingService pairings;
  private final PairingRepository pairingRows;
  private final LotteryService lotteries;
  private final LotteryRepository lotteryRows;
  private final LotteryTicketRepository tickets;
  private final UserRepository users;
  private final ObjectMapper json;

  public CommunityTestController(
    AdminPushAuth auth,
    PairingService pairings,
    PairingRepository pairingRows,
    LotteryService lotteries,
    LotteryRepository lotteryRows,
    LotteryTicketRepository tickets,
    UserRepository users,
    ObjectMapper json
  ) {
    this.auth = auth;
    this.pairings = pairings;
    this.pairingRows = pairingRows;
    this.lotteries = lotteries;
    this.lotteryRows = lotteryRows;
    this.tickets = tickets;
    this.users = users;
    this.json = json;
  }

  public record Create(
    Long userId,
    int count,
    int durationSeconds,
    String requestId
  ) {}

  public record Command(
    String action,
    Long actor,
    String choice,
    Map<Long, Integer> scores,
    int revision,
    int round,
    int game,
    int durationSeconds
  ) {}

  private void require(boolean valid) {
    if (!valid) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Əməliyyat yalnız öz sınaq məlumatınız üçün mümkündür."
    );
  }

  @GetMapping
  public Map<String, Object> sessions(
    @RequestHeader("Authorization") String key,
    @RequestParam Long userId
  ) {
    Long admin = auth.requireAdmin(key).getId();
    return Map.of(
      "pairings",
      pairingRows
        .findByDeletedFalseOrderByCreatedAtDesc()
        .stream()
        .filter(
          p ->
            p.isTestMode() &&
            Objects.equals(admin, p.getCreatedBy()) &&
            Objects.equals(userId, p.getTestUserId())
        )
        .map(p -> Map.of("id", p.getId(), "title", p.getTitle()))
        .toList(),
      "lotteries",
      lotteryRows
        .findByDeletedFalseOrderByCreatedAtDesc()
        .stream()
        .filter(
          l ->
            l.isTestMode() &&
            Objects.equals(admin, l.getCreatedBy()) &&
            Objects.equals(userId, l.getTestUserId())
        )
        .map(l -> Map.of("id", l.getId(), "title", l.getTitle()))
        .toList()
    );
  }

  @PostMapping("/pairings")
  public Map<String, Object> createPairing(
    @RequestHeader("Authorization") String key,
    @RequestBody Create r
  ) {
    return pairings.createTest(
      r.userId(),
      r.count(),
      r.durationSeconds(),
      auth.requireAdmin(key).getId(),
      r.requestId()
    );
  }

  @GetMapping("/pairings/{id}")
  public Map<String, Object> pairing(
    @RequestHeader("Authorization") String key,
    @PathVariable Long id
  ) {
    pairings.requireTest(id, auth.requireAdmin(key).getId());
    return pairings.detail(id, null);
  }

  @PostMapping("/pairings/{id}")
  @Transactional
  public Map<String, Object> pairingAction(
    @RequestHeader("Authorization") String key,
    @PathVariable Long id,
    @RequestBody Command c
  ) throws Exception {
    Long admin = auth.requireAdmin(key).getId();
    pairings.requireTest(id, admin);
    switch (Objects.toString(c.action(), "")) {
      case "start" -> pairings.start(
        id,
        new PairingService.Start(null, null, c.durationSeconds())
      );
      case "reveal" -> pairings.revealTest(id, admin);
      case "notify" -> pairings.notify(
        id,
        "participants",
        UUID.randomUUID().toString()
      );
      case "result" -> {
        var row = pairingRows.lock(id).orElseThrow();
        var state = json.readValue(row.getStateJson(), PairingState.class);
        require(state.participants.contains(c.actor()));
        pairings.result(
          id,
          c.round(),
          c.game(),
          c.actor(),
          new PairingService.Result(c.choice(), c.scores(), c.revision()),
          false
        );
      }
      case "complete-group", "conflict" -> {
        pairings.reset(id, c.round(), c.game());
        var row = pairingRows.lock(id).orElseThrow();
        var state = json.readValue(row.getStateJson(), PairingState.class);
        var g = state.rounds.get(c.round() - 1).games.get(c.game());
        int revision = g.revision;
        if ("conflict".equals(c.action())) {
          for (Long actor : g.users.subList(0, 2))
            pairings.result(
              id,
              c.round(),
              c.game(),
              actor,
              new PairingService.Result("won", null, revision),
              false
            );
        } else {
          Long winner = c.actor() == null ? g.users.get(0) : c.actor();
          require(g.users.contains(winner));
          Map<Long, Integer> scores = new LinkedHashMap<>();
          for (Long actor : g.users) {
            pairings.result(
              id,
              c.round(),
              c.game(),
              actor,
              new PairingService.Result(
                actor.equals(winner) ? "won" : "lost",
                null,
                revision
              ),
              false
            );
            scores.put(actor, actor.equals(winner) ? 4 : 1);
          }
          for (Long actor : g.users)
            pairings.result(
              id,
              c.round(),
              c.game(),
              actor,
              new PairingService.Result(null, scores, revision),
              false
            );
        }
      }
      case "reset-game" -> pairings.reset(id, c.round(), c.game());
      case "delete" -> {
        var row = pairingRows.lock(id).orElseThrow();
        row.setDeleted(true);
        pairingRows.save(row);
      }
      default -> throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Yanlış sınaq əməliyyatı."
      );
    }
    return "delete".equals(c.action())
      ? Map.of("deleted", true)
      : pairings.detail(id, null);
  }

  @PostMapping("/lotteries")
  @Transactional
  public LotteryService.View createLottery(
    @RequestHeader("Authorization") String key,
    @RequestBody Create c
  ) {
    Long admin = auth.requireAdmin(key).getId();
    require(
      users
        .findById(c.userId())
        .filter(u -> !Boolean.TRUE.equals(u.getIsDisable()))
        .isPresent()
    );
    // A stable request ID also prevents retrying a timed-out creation from issuing a second test lottery.
    require(
      c.requestId() != null && c.requestId().matches("[a-zA-Z0-9-]{16,64}")
    );
    var old = lotteryRows.findByCreatedByAndTestRequestId(admin, c.requestId());
    if (old.isPresent()) {
      require(
        old.get().isTestMode() &&
          Objects.equals(old.get().getTestUserId(), c.userId())
      );
      return lotteries.detail(old.get().getId(), c.userId(), false);
    }
    var created = lotteries.create(
      new LotteryService.Form(
        "[Sınaq] Lotoreya",
        "Yalnız sınaq hesabı üçün. Digər biletlər virtual iştirakçılara aiddir.",
        "https://mototaksi.az/favicon.ico",
        null,
        new BigDecimal("5.00"),
        3
      ),
      admin
    );
    var row = lotteryRows.lock(created.id()).orElseThrow();
    row.setTestMode(true);
    row.setTestUserId(c.userId());
    row.setTestRequestId(c.requestId());
    lotteryRows.save(row);
    for (int i = 1; i <= 3; i++) {
      var t = new LotteryTicket();
      t.setLotteryId(row.getId());
      t.setNumber(i);
      t.setUserId(i == 1 ? c.userId() : -((long) (i - 1)));
      t.setRequestId(c.requestId());
      t.setIssuedBy(admin);
      t.setCreatedAt(System.currentTimeMillis());
      tickets.save(t);
    }
    row.setSoldTickets(3);
    lotteryRows.saveAndFlush(row);
    return lotteries.detail(row.getId(), c.userId(), false);
  }

  private Lottery testLottery(Long id, Long admin) {
    var l = lotteryRows
      .lock(id)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Sınaq tapılmadı.")
      );
    require(
      l.isTestMode() &&
        Objects.equals(admin, l.getCreatedBy()) &&
        !l.isDeleted()
    );
    return l;
  }

  @GetMapping("/lotteries/{id}")
  @Transactional
  public LotteryService.View lottery(
    @RequestHeader("Authorization") String key,
    @PathVariable Long id
  ) {
    var l = testLottery(id, auth.requireAdmin(key).getId());
    return lotteries.detail(id, l.getTestUserId(), false);
  }

  @PostMapping("/lotteries/{id}")
  @Transactional
  public Object lotteryAction(
    @RequestHeader("Authorization") String key,
    @PathVariable Long id,
    @RequestBody Command c
  ) {
    Long admin = auth.requireAdmin(key).getId();
    var l = testLottery(id, admin);
    switch (Objects.toString(c.action(), "")) {
      case "announce" -> lotteries.announce(id, UUID.randomUUID().toString());
      case "live" -> lotteries.open(
        id,
        new LotteryService.Open(
          true,
          true,
          Math.max(5, Math.min(60, c.durationSeconds()))
        )
      );
      case "notify" -> lotteries.notifyParticipants(id);
      case "spin-win", "spin-lose" -> {
        if ("ACTIVE".equals(l.getStatus())) lotteries.open(
          id,
          new LotteryService.Open(
            true,
            true,
            Math.max(5, Math.min(60, c.durationSeconds()))
          )
        );
        lotteries.spin(id);
        l = lotteryRows.lock(id).orElseThrow();
        int winner = "spin-win".equals(c.action()) ? 1 : 2;
        l.setWinnerNumber(winner);
        double landing =
          (2 * Math.PI - ((winner - 1) * 2 * Math.PI) / 3) % (2 * Math.PI);
        l.setFinalRotation(
          2 *
            Math.PI *
            Math.max(8, (int) Math.ceil(l.getDurationSeconds() / 3.0)) +
            landing
        );
        lotteryRows.saveAndFlush(l);
      }
      case "finish" -> {
        require("DRAWING".equals(l.getStatus()));
        l.setStartedAt(
          System.currentTimeMillis() - l.getDurationSeconds() * 1000L - 1
        );
        l.setEndsAt(System.currentTimeMillis() - 1);
        lotteryRows.saveAndFlush(l);
        lotteries.finish(id);
      }
      case "delete" -> {
        l.setDeleted(true);
        lotteryRows.save(l);
        return Map.of("deleted", true);
      }
      default -> throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Yanlış sınaq əməliyyatı."
      );
    }
    return lotteries.detail(id, l.getTestUserId(), false);
  }
}

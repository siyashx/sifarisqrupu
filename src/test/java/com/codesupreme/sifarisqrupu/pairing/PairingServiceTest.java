package com.codesupreme.sifarisqrupu.pairing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.LotteryUserDirectory;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

class PairingServiceTest {

  SimpMessagingTemplate events;
  PairingRepository repo;
  UserRepository users;
  FcmPushService fcm;
  PairingService service;
  Map<Long, Pairing> db;
  ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setup() {
    repo = mock(PairingRepository.class);
    users = mock(UserRepository.class);
    fcm = mock(FcmPushService.class);
    db = new HashMap<>();
    when(repo.saveAndFlush(any())).thenAnswer(i -> {
      Pairing p = i.getArgument(0);
      if (p.getId() == null) p.setId((long) db.size() + 1);
      db.put(p.getId(), p);
      return p;
    });
    when(repo.findById(anyLong())).thenAnswer(i ->
      Optional.ofNullable(db.get(i.getArgument(0)))
    );
    when(repo.lock(anyLong())).thenAnswer(i ->
      Optional.ofNullable(db.get(i.getArgument(0)))
    );
    when(users.findAllById(any())).thenAnswer(i -> {
      Iterable<Long> ids = i.getArgument(0);
      List<User> list = new ArrayList<>();
      ids.forEach(id ->
        list.add(
          User.builder()
            .id(id)
            .name("Person " + id)
            .build()
        )
      );
      return list;
    });
    events = mock(SimpMessagingTemplate.class);
    service = new PairingService(
      repo,
      users,
      mock(LotteryUserDirectory.class),
      json,
      fcm,
      events
    );
  }

  long create(int count, int size) {
    return (
      (Number) service
        .create(
          new PairingService.Form(
            "PES",
            "Tournament",
            size,
            java.util.stream.LongStream.rangeClosed(1, count).boxed().toList(),
            60
          ),
          1L
        )
        .get("id")
    ).longValue();
  }

  PairingState state(long id) throws Exception {
    return json.readValue(db.get(id).getStateJson(), PairingState.class);
  }

  void reveal(long id) throws Exception {
    var s = state(id);
    var r = s.rounds.get(s.rounds.size() - 1);
    r.startedAt = 1;
    r.endsAt = 2;
    db.get(id).setStateJson(json.writeValueAsString(s));
    service.finishAnimation(id);
  }

  void completeRound(long id) throws Exception {
    var r = state(id).rounds.get(state(id).rounds.size() - 1);
    for (var g : r.games) {
      Map<Long, Integer> scores = new HashMap<>();
      for (int i = 0; i < g.users.size(); i++) scores.put(
        g.users.get(i),
        i == 0 ? 4 : 1
      );
      service.result(
        id,
        r.number,
        g.index,
        null,
        new PairingService.Result(null, scores, g.revision),
        true
      );
    }
  }

  @Test
  void fullEightPlayerBracketHasFinalThirdPlaceAndThreePointWins()
    throws Exception {
    long id = create(8, 2);
    assertEquals(0, state(id).rounds.size());
    for (int round = 0; round < 3; round++) {
      service.start(id, new PairingService.Start(null, null, 60));
      reveal(id);
      completeRound(id);
    }
    var s = state(id);
    assertEquals(4, s.rounds.get(0).games.size());
    assertEquals(2, s.rounds.get(1).games.size());
    assertEquals("FINAL", s.rounds.get(2).stage);
    assertEquals(2, s.rounds.get(2).games.size());
    assertTrue(s.rounds.get(2).games.get(1).placement);
    var detail = service.detail(id, 1L);
    assertEquals("COMPLETED", detail.get("status"));
    assertEquals(3, ((List<?>) detail.get("podium")).size());
    for (var raw : (List<?>) detail.get("leaderboard")) {
      var row = (Map<?, ?>) raw;
      assertEquals(
        ((Number) row.get("wins")).intValue() * 3,
        ((Number) row.get("points")).intValue()
      );
    }
  }

  @Test
  void sixPlayersGetAByeInLaterRoundWithoutFakePoints() throws Exception {
    long id = create(6, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    completeRound(id);
    service.start(id, new PairingService.Start(null, null, 60));
    var r = state(id).rounds.get(1);
    assertEquals(1, r.byes.size());
    assertEquals(1, r.games.size());
    reveal(id);
    completeRound(id);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    completeRound(id);
    assertEquals("COMPLETED", service.detail(id, 1L).get("status"));
  }

  @Test
  void contradictoryVotesResetAndScoresRequireEveryParticipantConfirmation()
    throws Exception {
    long id = create(2, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    var g = state(id).rounds.get(0).games.get(0);
    long a = g.users.get(0),
      b = g.users.get(1);
    service.result(
      id,
      1,
      0,
      a,
      new PairingService.Result("won", null, 0),
      false
    );
    service.result(
      id,
      1,
      0,
      b,
      new PairingService.Result("won", null, 0),
      false
    );
    g = state(id).rounds.get(0).games.get(0);
    assertTrue(g.votes.isEmpty());
    assertEquals(1, g.revision);
    assertThrows(ResponseStatusException.class, () ->
      service.result(
        id,
        1,
        0,
        a,
        new PairingService.Result("won", null, 0),
        false
      )
    );
    service.result(
      id,
      1,
      0,
      a,
      new PairingService.Result("won", null, 1),
      false
    );
    service.result(
      id,
      1,
      0,
      b,
      new PairingService.Result("lost", null, 1),
      false
    );
    assertThrows(ResponseStatusException.class, () ->
      service.result(
        id,
        1,
        0,
        999L,
        new PairingService.Result(null, Map.of(a, 4, b, 1), 1),
        false
      )
    );
    service.result(
      id,
      1,
      0,
      a,
      new PairingService.Result(null, Map.of(a, 4, b, 1), 1),
      false
    );
    assertFalse(state(id).rounds.get(0).games.get(0).confirmed);
    service.result(
      id,
      1,
      0,
      b,
      new PairingService.Result(null, Map.of(a, 4, b, 1), 1),
      false
    );
    assertTrue(state(id).rounds.get(0).games.get(0).confirmed);
    assertEquals("COMPLETED", service.detail(id, a).get("status"));
  }

  @Test
  void changedScoresDiscardPreviousConfirmations() throws Exception {
    long id = create(2, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    var g = state(id).rounds.get(0).games.get(0);
    long a = g.users.get(0),
      b = g.users.get(1);
    service.result(
      id,
      1,
      0,
      a,
      new PairingService.Result("won", null, 0),
      false
    );
    service.result(
      id,
      1,
      0,
      b,
      new PairingService.Result("lost", null, 0),
      false
    );
    service.result(
      id,
      1,
      0,
      a,
      new PairingService.Result(null, Map.of(a, 4, b, 1), 0),
      false
    );
    service.result(
      id,
      1,
      0,
      b,
      new PairingService.Result(null, Map.of(a, 5, b, 1), 0),
      false
    );
    g = state(id).rounds.get(0).games.get(0);
    assertFalse(g.confirmed);
    assertEquals(Set.of(b), g.confirmations);
  }

  @Test
  void unseenDrawAssignmentsAreHiddenFromAdminAndUser() throws Exception {
    long id = create(8, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    var detail = service.detail(id, 1L);
    var rounds = (List<?>) detail.get("rounds");
    var games = (List<?>) ((Map<?, ?>) rounds.get(0)).get("games");
    for (var raw : games) {
      var g = (Map<?, ?>) raw;
      assertEquals(List.of(), g.get("users"));
      assertFalse((Boolean) g.get("revealed"));
    }
  }

  @Test
  void correctingEarlierResultInvalidatesDependentRounds() throws Exception {
    long id = create(8, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    completeRound(id);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    completeRound(id);
    service.reset(id, 1, 0);
    assertEquals(1, state(id).rounds.size());
    assertFalse(state(id).rounds.get(0).games.get(0).confirmed);
  }

  @Test
  void groupOfThreeHasOneWinnerAndNoDuplicatePlayers() throws Exception {
    long id = create(9, 3);
    service.start(id, new PairingService.Start(null, null, 60));
    var first = state(id).rounds.get(0);
    assertEquals(3, first.games.size());
    assertEquals(
      9,
      first.games
        .stream()
        .flatMap(g -> g.users.stream())
        .distinct()
        .count()
    );
    reveal(id);
    completeRound(id);
    service.start(id, new PairingService.Start(null, null, 60));
    reveal(id);
    completeRound(id);
    assertEquals(3, ((List<?>) service.detail(id, 1L).get("podium")).size());
  }

  @Test
  void validatesInitialDivisionAndUsesOnlyFirstProfilePortrait() {
    assertThrows(ResponseStatusException.class, () -> create(5, 2));
    var u = User.builder()
      .identifyPhoto(List.of("https://example.com/portrait.jpg", "https://example.com/id-card.jpg"))
      .build();
    assertEquals("https://example.com/portrait.jpg", PairingService.avatar(u));
    assertNull(PairingService.avatar(User.builder().identifyPhoto(List.of()).build()));
  }

  @Test
  void cannotStartBeforeResultsOrPrematurelyFinish() {
    long id = create(8, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    assertThrows(ResponseStatusException.class, () ->
      service.start(id, new PairingService.Start(null, null, 60))
    );
    assertThrows(ResponseStatusException.class, () ->
      service.status(id, "COMPLETED")
    );
  }

  @Test
  void notificationAudienceAndRetryDoNotDuplicateAnIntent() {
    long id = create(2, 2);
    when(users.findNotificationRecipientIds()).thenReturn(List.of(1L, 2L, 9L));
    when(
      fcm.enqueue(
        anyString(),
        anyCollection(),
        anyString(),
        anyString(),
        anyMap(),
        anyInt(),
        anyString()
      )
    ).thenReturn(2);
    assertEquals(
      0,
      service.notify(id, "none", "none-request-123456789").get("queued")
    );
    verifyNoInteractions(fcm);
    assertEquals(
      4,
      service
        .notify(id, "participants", "participant-request-1234")
        .get("queued")
    );
    assertEquals(
      4,
      service
        .notify(id, "participants", "participant-request-1234")
        .get("queued")
    );
    verify(fcm, times(2)).enqueue(
      anyString(),
      eq(List.of(1L, 2L)),
      anyString(),
      anyString(),
      argThat(d -> "pairing".equals(d.get("scope"))),
      eq(86400),
      anyString()
    );
    service.notify(id, "all", "all-request-1234567890");
    verify(fcm, times(2)).enqueue(
      anyString(),
      eq(List.of(1L, 2L, 9L)),
      anyString(),
      anyString(),
      anyMap(),
      eq(86400),
      anyString()
    );
  }

  @Test
  void eachRevealedGroupPublishesOneLiveRefreshWithoutWritingAResult()
    throws Exception {
    long id = create(4, 2);
    service.start(id, new PairingService.Start(null, null, 60));
    var s = state(id);
    var r = s.rounds.get(0);
    long now = System.currentTimeMillis();
    r.startedAt = now - 5000;
    r.endsAt = now + 3000;
    db.get(id).setStateJson(json.writeValueAsString(s));
    clearInvocations(events, repo);
    service.finishAnimation(id);
    service.finishAnimation(id);
    verify(events, times(1)).convertAndSend(
      eq("/topic/pairings"),
      eq(Map.of("pairingId", id))
    );
    verify(repo, never()).saveAndFlush(any());
    assertEquals("DRAWING", db.get(id).getStatus());
  }
}

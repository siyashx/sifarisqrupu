package com.codesupreme.sifarisqrupu.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.*;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.pairing.*;
import com.codesupreme.sifarisqrupu.push.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
  properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:communityqa;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
    "spring.jpa.show-sql=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
  }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CommunityTestIntegrationTest.Config.class)
class CommunityTestIntegrationTest {

  @Configuration
  @EntityScan(
    basePackageClasses = {
      Pairing.class,
      Lottery.class,
      PushDelivery.class,
      User.class,
    }
  )
  @EnableJpaRepositories(
    basePackageClasses = {
      PairingRepository.class,
      LotteryRepository.class,
      PushDeliveryRepository.class,
    }
  )
  @Import({
    PairingService.class,
    LotteryService.class,
    CommunityTestController.class,
  })
  static class Config {

    @Bean
    ObjectMapper json() {
      return new ObjectMapper();
    }
  }

  @MockBean
  AdminPushAuth auth;

  @MockBean
  UserRepository users;

  @MockBean
  LotteryUserDirectory directory;

  @MockBean
  FcmPushService fcm;

  @MockBean
  SimpMessagingTemplate events;

  @Autowired
  CommunityTestController qa;

  @Autowired
  PairingService pairings;

  @Autowired
  PairingRepository pairs;

  @Autowired
  LotteryService lotteries;

  @Autowired
  LotteryRepository lotteryRows;

  @Autowired
  LotteryTicketRepository tickets;

  @BeforeEach
  void setup() {
    when(auth.requireAdmin("admin")).thenReturn(Admin.builder().id(1L).build());
    when(auth.requireAdmin("user")).thenThrow(
      new ResponseStatusException(
        org.springframework.http.HttpStatus.UNAUTHORIZED
      )
    );
    when(users.findById(7L)).thenReturn(
      Optional.of(User.builder().id(7L).name("Real test account").build())
    );
    when(users.findAllById(any())).thenAnswer(i -> {
      List<User> found = new ArrayList<>();
      for (Long id : (Iterable<Long>) i.getArgument(0))
        if (id == 7L) found.add(
          User.builder().id(7L).name("Real test account").build()
        );
      return found;
    });
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
    ).thenReturn(1);
  }

  CommunityTestController.Command cmd(String action) {
    return new CommunityTestController.Command(
      action,
      null,
      null,
      null,
      0,
      0,
      0,
      15
    );
  }

  @Test
  void authenticationAndTestGuardPreventMutatingARealTournament() {
    assertThrows(ResponseStatusException.class, () ->
      qa.createPairing(
        "user",
        new CommunityTestController.Create(7L, 2, 15, "request-1234567890")
      )
    );
    var p = new Pairing();
    p.setTitle("Real");
    p.setCreatedBy(1L);
    p.setStateJson("{}");
    pairs.saveAndFlush(p);
    assertThrows(ResponseStatusException.class, () ->
      qa.pairingAction("admin", p.getId(), cmd("delete"))
    );
    assertFalse(pairs.findById(p.getId()).orElseThrow().isDeleted());
  }

  @Test
  void syntheticParticipantsUseOrdinaryVoteAndConfirmationRulesAndStayPrivate()
    throws Exception {
    var c = new CommunityTestController.Create(
      7L,
      2,
      15,
      "pair-request-1234567890"
    );
    var p = qa.createPairing("admin", c);
    Long id = ((Number) p.get("id")).longValue();
    assertEquals(id, qa.createPairing("admin", c).get("id"));
    assertEquals(0, pairings.list(999L).size());
    assertThrows(ResponseStatusException.class, () ->
      pairings.detail(id, 999L)
    );
    qa.pairingAction("admin", id, cmd("start"));
    qa.pairingAction("admin", id, cmd("reveal"));
    for (Long actor : List.of(7L, -1L))
      qa.pairingAction(
        "admin",
        id,
        new CommunityTestController.Command(
          "result",
          actor,
          "won",
          null,
          0,
          1,
          0,
          15
        )
      );
    var game = (Map<?, ?>) (
      (List<?>) (
        (Map<?, ?>) ((List<?>) qa.pairing("admin", id).get("rounds")).get(0)
      ).get("games")
    ).get(0);
    assertEquals(1, game.get("revision"));
    assertEquals(Map.of(), game.get("votes"));
    qa.pairingAction(
      "admin",
      id,
      new CommunityTestController.Command(
        "result",
        7L,
        "won",
        null,
        1,
        1,
        0,
        15
      )
    );
    qa.pairingAction(
      "admin",
      id,
      new CommunityTestController.Command(
        "result",
        -1L,
        "lost",
        null,
        1,
        1,
        0,
        15
      )
    );
    qa.pairingAction(
      "admin",
      id,
      new CommunityTestController.Command(
        "result",
        7L,
        null,
        Map.of(7L, 4, -1L, 1),
        1,
        1,
        0,
        15
      )
    );
    assertNotEquals("COMPLETED", qa.pairing("admin", id).get("status"));
    qa.pairingAction(
      "admin",
      id,
      new CommunityTestController.Command(
        "result",
        -1L,
        null,
        Map.of(7L, 4, -1L, 1),
        1,
        1,
        0,
        15
      )
    );
    assertEquals("COMPLETED", qa.pairing("admin", id).get("status"));
    verify(fcm, atLeastOnce()).enqueue(
      anyString(),
      eq(List.of(7L)),
      anyString(),
      anyString(),
      anyMap(),
      anyInt(),
      anyString()
    );
    assertEquals(
      1,
      qa.sessions("admin", 7L).get("pairings") instanceof List<?> l
        ? l.size()
        : 0
    );
    assertEquals(List.of(), qa.sessions("admin", 999L).get("pairings"));
  }

  @Test
  void lotteryScenariosAreIsolatedAndRetryDoesNotDuplicateCreation() {
    var c = new CommunityTestController.Create(
      7L,
      2,
      15,
      "lottery-request-123456789"
    );
    var l = qa.createLottery("admin", c);
    assertEquals(l.id(), qa.createLottery("admin", c).id());
    assertEquals(
      3,
      tickets.findByLotteryIdAndRevokedFalseOrderByNumber(l.id()).size()
    );
    assertEquals(List.of(), lotteries.list(999L, false));
    assertThrows(ResponseStatusException.class, () ->
      lotteries.detail(l.id(), 999L, false)
    );
    qa.lotteryAction("admin", l.id(), cmd("announce"));
    verify(fcm, times(2)).enqueue(
      anyString(),
      eq(List.of(7L)),
      anyString(),
      anyString(),
      argThat(d -> "created".equals(d.get("event"))),
      anyInt(),
      anyString()
    );
    qa.lotteryAction("admin", l.id(), cmd("spin-lose"));
    var result = (LotteryService.View) qa.lotteryAction(
      "admin",
      l.id(),
      cmd("finish")
    );
    assertEquals("COMPLETED", result.status());
    assertEquals("00002", result.winnerTicket());
    assertEquals("Sınaq iştirakçısı 1", result.winnerName());
    verify(fcm, times(2)).enqueue(
      anyString(),
      eq(List.of(7L)),
      anyString(),
      anyString(),
      argThat(d -> "lost".equals(d.get("event"))),
      anyInt(),
      anyString()
    );
  }

  @Test
  void eightVirtualPlayersCanCompleteSemifinalFinalAndThirdPlaceUsingMemberRules()
    throws Exception {
    Long id = (
      (Number) qa
        .createPairing(
          "admin",
          new CommunityTestController.Create(
            7L,
            8,
            15,
            "eight-person-test-12345"
          )
        )
        .get("id")
    ).longValue();
    for (int stage = 0; stage < 3; stage++) {
      qa.pairingAction("admin", id, cmd("start"));
      qa.pairingAction("admin", id, cmd("reveal"));
      var state = new ObjectMapper().readValue(
        pairs.findById(id).orElseThrow().getStateJson(),
        PairingState.class
      );
      var round = state.rounds.get(state.rounds.size() - 1);
      for (var g : round.games)
        qa.pairingAction(
          "admin",
          id,
          new CommunityTestController.Command(
            "complete-group",
            g.users.get(0),
            null,
            null,
            0,
            round.number,
            g.index,
            15
          )
        );
    }
    var result = qa.pairing("admin", id);
    assertEquals("COMPLETED", result.get("status"));
    assertEquals(3, ((List<?>) result.get("podium")).size());
  }

  @Test
  void forcedWinRunsTheRealLotteryResultNotificationPipeline() {
    var l = qa.createLottery(
      "admin",
      new CommunityTestController.Create(7L, 2, 15, "won-test-lottery-12345")
    );
    qa.lotteryAction("admin", l.id(), cmd("spin-win"));
    var result = (LotteryService.View) qa.lotteryAction(
      "admin",
      l.id(),
      cmd("finish")
    );
    assertEquals("00001", result.winnerTicket());
    assertEquals("Real test account", result.winnerName());
    verify(fcm, times(2)).enqueue(
      anyString(),
      eq(List.of(7L)),
      anyString(),
      anyString(),
      argThat(d -> "won".equals(d.get("event"))),
      anyInt(),
      anyString()
    );
  }

  @Test
  void contactProfileNeverIncludesThePassword() {
    var memberAuth = mock(PushAuth.class);
    when(memberAuth.requireUser("member")).thenReturn(
      User.builder()
        .id(7L)
        .name("Name")
        .phoneNumber("0709559984")
        .password("private")
        .build()
    );
    var controller = new CommunityProfileController(memberAuth);
    assertEquals(
      Set.of("id", "name", "phoneNumber"),
      controller.me("member").keySet()
    );
  }
}

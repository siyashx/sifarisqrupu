package com.codesupreme.sifarisqrupu.pairing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.LotteryUserDirectory;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;

@DataJpaTest(
  properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:pairing;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
    "spring.jpa.show-sql=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
  }
)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
  replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE
)
@ContextConfiguration(classes = PairingIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PairingIntegrationTest {

  @TestConfiguration
  @EntityScan(basePackageClasses = { Pairing.class, User.class })
  @EnableJpaRepositories(basePackageClasses = PairingRepository.class)
  @Import(PairingService.class)
  static class Config {

    @Bean
    ObjectMapper json() {
      return new ObjectMapper();
    }
  }

  @Autowired
  PairingRepository repo;

  @Autowired
  PairingService service;

  @Autowired
  ObjectMapper json;

  @MockBean
  UserRepository users;

  @MockBean
  LotteryUserDirectory directory;

  @MockBean
  FcmPushService fcm;

  @MockBean
  SimpMessagingTemplate events;

  @BeforeEach
  void setup() {
    repo.deleteAll();
    when(users.findAllById(any())).thenAnswer(i -> {
      List<User> result = new ArrayList<>();
      ((Iterable<Long>) i.getArgument(0)).forEach(id ->
        result.add(
          User.builder()
            .id(id)
            .name("Player " + id)
            .build()
        )
      );
      return result;
    });
  }

  @Test
  void duplicateCreateIntentAndConcurrentConflictingVotesAreSafe()
    throws Exception {
    var form = new PairingService.Form(
      "PES",
      "",
      2,
      List.of(1L, 2L),
      30,
      "create-intent-123456789"
    );
    long id = ((Number) service.create(form, 1L).get("id")).longValue();
    assertEquals(id, ((Number) service.create(form, 1L).get("id")).longValue());
    assertEquals(1, repo.count());
    service.start(id, new PairingService.Start(null, null, 30));
    var entity = repo.findById(id).orElseThrow();
    var state = json.readValue(entity.getStateJson(), PairingState.class);
    state.rounds.get(0).startedAt = 1;
    state.rounds.get(0).endsAt = 2;
    entity.setStateJson(json.writeValueAsString(state));
    repo.saveAndFlush(entity);
    service.finishAnimation(id);
    var pool = Executors.newFixedThreadPool(2);
    var latch = new CountDownLatch(1);
    try {
      List<Future<?>> work = new ArrayList<>();
      for (long uid : List.of(1L, 2L))
        work.add(
          pool.submit(() -> {
            try {
              latch.await();
              service.result(
                id,
                1,
                0,
                uid,
                new PairingService.Result("won", null, 0),
                false
              );
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(e);
            }
          })
        );
      latch.countDown();
      for (var job : work) job.get(15, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    var finalState = json.readValue(
      repo.findById(id).orElseThrow().getStateJson(),
      PairingState.class
    );
    assertTrue(finalState.rounds.get(0).games.get(0).votes.isEmpty());
    assertEquals(1, finalState.rounds.get(0).games.get(0).revision);
  }
}

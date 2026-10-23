package com.codesupreme.sifarisqrupu.lottery;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
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
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:lottery;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","spring.jpa.show-sql=false","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=LotteryIntegrationTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LotteryIntegrationTest {
    @TestConfiguration @EntityScan(basePackageClasses={Lottery.class,User.class})
    @EnableJpaRepositories(basePackageClasses=LotteryRepository.class) @Import(LotteryService.class)
    static class Config {}
    @Autowired LotteryService service;
    @Autowired LotteryRepository lotteries;
    @Autowired LotteryTicketRepository tickets;
    @MockBean UserRepository users;
    @MockBean FcmPushService fcm;
    @MockBean SimpMessagingTemplate events;
    @BeforeEach void setup() {
        tickets.deleteAll();lotteries.deleteAll();
        when(users.findAllById(any())).thenAnswer(call->{var list=new ArrayList<User>();for(Long id:call.<Iterable<Long>>getArgument(0)) list.add(User.builder().id(id).name("User "+id).phoneNumber("0551234567").isDisable(false).build());return list;});
        when(users.findById(anyLong())).thenAnswer(call->Optional.of(User.builder().id(call.getArgument(0)).name("Winner").phoneNumber("0551234567").build()));
    }
    LotteryService.Form form(int total) {return new LotteryService.Form("Hədiyyə","","https://example.test/image.jpg",System.currentTimeMillis()+86400000,new BigDecimal("2.50"),total);}
    Long create(int total) {return service.create(form(total),1L).id();}
    LotteryService.Issue issue(long user) {return new LotteryService.Issue(List.of(user),UUID.randomUUID().toString());}
    @Test void sequentialTicketsAndRetryDoNotDoubleIssue() {
        Long id=create(3);var request=issue(7);
        assertEquals(List.of("00001"),service.issue(id,request,1L));
        assertEquals(List.of("00001"),service.issue(id,request,1L));
        assertEquals(List.of("00002"),service.issue(id,issue(7),1L));
        assertEquals(List.of("00003"),service.issue(id,issue(8),1L));
        assertThrows(ResponseStatusException.class,()->service.issue(id,issue(9),1L));
        assertEquals(3,tickets.count());assertEquals(3,service.detail(id,null,true).soldTickets());
    }
    @Test void concurrentLastTicketHasExactlyOneOwner() throws Exception {
        Long id=create(1);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var jobs=new ArrayList<Future<Boolean>>();
            for(long user:List.of(7L,8L)) jobs.add(pool.submit(()->{start.await();try {service.issue(id,issue(user),1L);return true;}catch(ResponseStatusException e){return false;}}));
            start.countDown();int successes=0;for(var job:jobs) if(job.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);assertEquals(1,tickets.count());assertEquals(1,service.detail(id,null,true).soldTickets());
        } finally {pool.shutdownNow();}
    }
    @Test void liveFreezesSalesAndDateConfirmationIsServerEnforced() {
        Long id=create(3);service.issue(id,issue(7),1L);
        assertThrows(ResponseStatusException.class,()->service.open(id,new LotteryService.Open(false,false,30)));
        var live=service.open(id,new LotteryService.Open(false,true,30));assertEquals("LIVE",live.status());
        assertThrows(ResponseStatusException.class,()->service.issue(id,issue(8),1L));
        assertThrows(ResponseStatusException.class,()->service.edit(id,form(4)));
        assertEquals("CANCELLED",service.cancel(id).status());
        assertThrows(ResponseStatusException.class,()->service.spin(id));
    }
    @Test void sameBakuCalendarDateDoesNotRequireEarlyConfirmation() {
        Long id=create(2);var l=lotteries.findById(id).orElseThrow();l.setDrawAt(System.currentTimeMillis());lotteries.saveAndFlush(l);
        service.issue(id,issue(7),1L);
        assertEquals("LIVE",service.open(id,new LotteryService.Open(false,false,5)).status());
    }
    @Test void drawHasOneImmutableWinnerAndCompletionQueuesEachTicketOnce() {
        Long id=create(3);for(long user:List.of(7L,8L,9L)) service.issue(id,issue(user),1L);
        service.open(id,new LotteryService.Open(true,true,5));
        var drawing=service.spin(id);assertEquals("DRAWING",drawing.status());assertNull(drawing.winnerTicket());
        assertEquals(5000,drawing.endsAt()-drawing.startedAt());
        assertThrows(ResponseStatusException.class,()->service.spin(id));
        assertThrows(ResponseStatusException.class,()->service.cancel(id));
        var row=lotteries.findById(id).orElseThrow();int winner=row.getWinnerNumber();
        int pointer=Math.floorMod((int)Math.floor(-row.getFinalRotation()*3/(2*Math.PI)+0.5),3)+1;
        assertEquals(winner,pointer);
        row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);
        service.finish(id);service.finish(id);
        assertEquals("COMPLETED",service.detail(id,7L,false).status());
        verify(fcm,times(3)).enqueue(eq("ZAKAZ"),anyCollection(),anyString(),anyString(),argThat(m->m.containsKey("ticketCode")),eq(86400),anyString());
        assertNull(service.detail(id,7L,false).winner());
        assertNotNull(service.detail(id,null,true).winner());
    }
    @Test void participantWithManyTicketsGetsOneConsistentResultMessage() {
        Long id=create(3);for(int i=0;i<3;i++) service.issue(id,issue(7),1L);
        service.open(id,new LotteryService.Open(false,true,5));service.spin(id);
        var row=lotteries.findById(id).orElseThrow();row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);
        service.finish(id);
        verify(fcm,times(1)).enqueue(eq("ZAKAZ"),eq(List.of(7L)),eq("🎉 Qalib oldunuz!"),anyString(),argThat(m->"won".equals(m.get("event"))),eq(86400),anyString());
    }
    @Test void losingParticipantReceivesTheirCodesTogether() {
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);service.issue(id,issue(8),1L);
        service.open(id,new LotteryService.Open(false,true,5));service.spin(id);
        var row=lotteries.findById(id).orElseThrow();row.setWinnerNumber(1);row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);
        service.finish(id);
        verify(fcm,times(1)).enqueue(eq("ZAKAZ"),eq(List.of(8L)),eq("Lotoreyanın nəticəsi"),contains("00002, 00003"),argThat(m->"lost".equals(m.get("event"))),eq(86400),anyString());
        verify(fcm,times(2)).enqueue(eq("ZAKAZ"),anyCollection(),anyString(),anyString(),anyMap(),eq(86400),anyString());
    }
    @Test void privateTicketsDoNotLeakToOtherUsers() {
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);
        assertEquals(List.of("00001"),service.detail(id,7L,false).myTickets());
        assertEquals(List.of("00002"),service.detail(id,8L,false).myTickets());
        assertTrue(service.detail(id,9L,false).myTickets().isEmpty());
    }
    @Test void capacityEditCannotGoBelowSalesAndInvalidFormsAreRejected() {
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);
        assertThrows(ResponseStatusException.class,()->service.edit(id,form(1)));
        assertThrows(ResponseStatusException.class,()->service.create(form(100000),1L));
        assertThrows(ResponseStatusException.class,()->service.open(id,new LotteryService.Open(false,true,0)));
    }
}

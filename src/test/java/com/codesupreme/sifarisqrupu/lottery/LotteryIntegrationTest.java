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
    @Test void announcementIncludesCustomersWithoutTicketsAndEachResendHasANewEvent() {
        when(users.findAll()).thenReturn(List.of(
            User.builder().id(7L).userType("customer").isDisable(false).online(false).isSub(false).build(),
            User.builder().id(8L).userType("courier").isDisable(false).build(),
            User.builder().id(9L).isDisable(true).build()));
        Long id=create(3);
        service.announce(id);service.announce(id);
        for(String app:List.of("ZAKAZ","ELEHBER")) {
            verify(fcm).enqueue(eq(app),eq(List.of(7L,8L)),anyString(),anyString(),
                argThat(m->"created".equals(m.get("event")) && "lottery".equals(m.get("scope"))),eq(86400),eq("lottery-"+id+"-created-1"));
            verify(fcm).enqueue(eq(app),eq(List.of(7L,8L)),anyString(),anyString(),anyMap(),eq(86400),eq("lottery-"+id+"-created-2"));
        }
        service.cancel(id);
        assertThrows(ResponseStatusException.class,()->service.announce(id));
    }
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
        verify(fcm,times(1)).enqueue(eq("ELEHBER"),eq(List.of(7L)),eq("🎉 Qalib oldunuz!"),anyString(),argThat(m->"won".equals(m.get("event"))),eq(86400),anyString());
    }
    @Test void losingParticipantReceivesTheirCodesTogether() {
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);service.issue(id,issue(8),1L);
        service.open(id,new LotteryService.Open(false,true,5));service.spin(id);
        var row=lotteries.findById(id).orElseThrow();row.setWinnerNumber(1);row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);
        service.finish(id);
        verify(fcm,times(1)).enqueue(eq("ZAKAZ"),eq(List.of(8L)),eq("Lotoreyanın nəticəsi"),contains("00002, 00003"),argThat(m->"lost".equals(m.get("event"))),eq(86400),anyString());
        verify(fcm,times(1)).enqueue(eq("ELEHBER"),eq(List.of(8L)),eq("Lotoreyanın nəticəsi"),contains("00002, 00003"),argThat(m->"lost".equals(m.get("event"))),eq(86400),anyString());
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
    @Test void liveAndResendExposeQueueStatusAndUseDistinctEventIds() {
        when(fcm.isEnabled()).thenReturn(true);
        when(fcm.enqueue(anyString(),anyCollection(),anyString(),anyString(),anyMap(),anyInt(),anyString())).thenReturn(2);
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);
        var live=service.open(id,new LotteryService.Open(true,true,5));
        assertEquals(4,live.notification().queued());assertEquals(2,live.notification().participants());assertTrue(live.notification().fcmEnabled());
        assertTrue(live.createdAt()>0);
        assertEquals(4,service.notifyParticipants(id).queued());
        verify(fcm).enqueue(eq("ZAKAZ"),eq(new LinkedHashSet<>(List.of(7L,8L))),anyString(),anyString(),anyMap(),eq(300),eq("lottery-"+id+"-live-1"));
        verify(fcm).enqueue(eq("ZAKAZ"),anyCollection(),anyString(),anyString(),anyMap(),eq(300),eq("lottery-"+id+"-live-2"));
        verify(fcm).enqueue(eq("ELEHBER"),eq(new LinkedHashSet<>(List.of(7L,8L))),anyString(),anyString(),anyMap(),eq(300),eq("lottery-"+id+"-live-1"));
        verify(fcm).enqueue(eq("ELEHBER"),anyCollection(),anyString(),anyString(),anyMap(),eq(300),eq("lottery-"+id+"-live-2"));
    }
    @Test void disabledFcmIsReportedInsteadOfSilentSuccess() {
        when(fcm.isEnabled()).thenReturn(false);
        Long id=create(1);service.issue(id,issue(7),1L);
        var notice=service.open(id,new LotteryService.Open(true,true,5)).notification();
        assertEquals(0,notice.queued());assertFalse(notice.fcmEnabled());assertEquals(1,notice.participants());
    }
    @Test void revokedTicketLeavesOtherCodesUnchangedAndCodeIsNeverReused() {
        Long id=create(3);service.issue(id,issue(7),1L);var request=issue(8);service.issue(id,request,1L);service.issue(id,issue(9),1L);
        var updated=service.revokeTicket(id,"00002",42L);
        assertEquals(2,updated.soldTickets());assertEquals(2*100.0/3,updated.soldPercent(),.001);
        assertTrue(service.detail(id,8L,false).myTickets().isEmpty());
        assertEquals(List.of("00001","00003"),service.ticketPage(id,0).items().stream().map(LotteryService.TicketView::code).toList());
        assertEquals(2,service.revokeTicket(id,"00002",42L).soldTickets());
        var revoked=tickets.findByLotteryIdAndNumber(id,2).orElseThrow();assertTrue(revoked.isRevoked());assertEquals(42L,revoked.getRevokedBy());
        assertThrows(ResponseStatusException.class,()->service.issue(id,request,1L));
        assertEquals(List.of("00004"),service.issue(id,issue(10),1L));assertEquals(3,service.detail(id,null,true).soldTickets());
    }
    @Test void wheelAndResultUseOnlyEligibleCodesAfterRevocation() {
        Long id=create(3);service.issue(id,issue(7),1L);service.issue(id,issue(8),1L);service.issue(id,issue(9),1L);
        service.revokeTicket(id,"00002",1L);
        var live=service.open(id,new LotteryService.Open(false,true,5));assertEquals(List.of("00001","00003"),live.wheelTickets());
        var drawing=service.spin(id);var row=lotteries.findById(id).orElseThrow();
        int pointer=Math.floorMod((int)Math.floor(-drawing.finalRotation()*2/(2*Math.PI)+.5),2);
        assertEquals(String.format("%05d",row.getWinnerNumber()),drawing.wheelTickets().get(pointer));
        assertNotEquals(2,row.getWinnerNumber());row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);service.finish(id);
        verify(fcm,never()).enqueue(anyString(),argThat(ids->ids.contains(8L)),anyString(),anyString(),anyMap(),anyInt(),anyString());
        assertEquals(2,service.detail(id,7L,false).wheelTickets().size());
    }
    @Test void revokedOnlyTicketRestoresCapacityAndNewSingleTicketKeepsItsCode() {
        Long id=create(1);service.issue(id,issue(7),1L);service.revokeTicket(id,"00001",1L);
        assertThrows(ResponseStatusException.class,()->service.open(id,new LotteryService.Open(false,true,5)));
        assertEquals(List.of("00002"),service.issue(id,issue(8),1L));
        assertEquals(List.of("00002"),service.open(id,new LotteryService.Open(false,true,5)).wheelTickets());
    }
    @Test void deleteHidesLotteryAndTicketsFromAllPanelsButRetainsAudit() {
        Long id=create(2);service.issue(id,issue(7),1L);service.delete(id,42L);service.delete(id,42L);
        assertTrue(service.list(7L,false).isEmpty());assertTrue(service.list(null,true).isEmpty());
        assertThrows(ResponseStatusException.class,()->service.detail(id,7L,false));assertThrows(ResponseStatusException.class,()->service.detail(id,null,true));
        assertThrows(ResponseStatusException.class,()->service.ticketPage(id,0));assertThrows(ResponseStatusException.class,()->service.issue(id,issue(8),1L));
        var audit=lotteries.findById(id).orElseThrow();assertTrue(audit.isDeleted());assertEquals(42L,audit.getDeletedBy());assertEquals(1,tickets.count());
    }
    @Test void revokeStopsAtLiveAndDeletionWaitsForDrawingToFinish() {
        Long id=create(1);service.issue(id,issue(7),1L);service.open(id,new LotteryService.Open(false,true,5));
        assertThrows(ResponseStatusException.class,()->service.revokeTicket(id,"00001",1L));service.spin(id);
        assertThrows(ResponseStatusException.class,()->service.delete(id,1L));
        var row=lotteries.findById(id).orElseThrow();row.setEndsAt(System.currentTimeMillis()-1);lotteries.saveAndFlush(row);service.finish(id);
        assertThrows(ResponseStatusException.class,()->service.revokeTicket(id,"00001",1L));service.delete(id,1L);assertTrue(service.list(null,true).isEmpty());
    }
    @Test void concurrentRevocationIsIdempotentAndCannotMakeSalesNegative() throws Exception {
        Long id=create(1);service.issue(id,issue(7),1L);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{start.await();return service.revokeTicket(id,"00001",1L).soldTickets();});
            var two=pool.submit(()->{start.await();return service.revokeTicket(id,"00001",1L).soldTickets();});start.countDown();
            assertEquals(0,one.get(15,TimeUnit.SECONDS));assertEquals(0,two.get(15,TimeUnit.SECONDS));assertEquals(0,service.detail(id,null,true).soldTickets());
        } finally {pool.shutdownNow();}
    }
}

package com.codesupreme.sifarisqrupu.lottery;
import com.codesupreme.sifarisqrupu.push.*;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class LotteryControllerTest {
    LotteryService service;PushAuth members;AdminPushAuth admins;MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(LotteryService.class);members=mock(PushAuth.class);admins=mock(AdminPushAuth.class);
        when(members.requireUser(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(admins.requireAdmin(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(admins.requireAdmin("member-token")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc=MockMvcBuilders.standaloneSetup(new LotteryController(members,service),new AdminLotteryController(admins,service)).setControllerAdvice(new LotteryErrors()).build();
    }
    @Test void memberCannotSpinOrIssueTicketsThroughAdminApi() throws Exception {
        mvc.perform(post("/api/v5/push/admin/lotteries/42/spin").header("Authorization","member-token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v5/push/admin/lotteries/42/tickets").header("Authorization","member-token").contentType("application/json").content("{\"userIds\":[7],\"requestId\":\"request-1234567890\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void personalDetailUsesAuthenticatedIdentityNotAnArbitraryQueryParameter() throws Exception {
        when(members.requireUser("member-token")).thenReturn(User.builder().id(7L).build());
        mvc.perform(get("/api/v5/lotteries/42?userId=99").header("Authorization","member-token")).andExpect(status().isOk());
        verify(service).detail(42L,7L,false);
    }
    @Test void anonymousReadIsRejected() throws Exception {
        mvc.perform(get("/api/v5/lotteries")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void customersReceiveOwnLotteryEventsRegardlessOfWorkNotificationMute() {
        var device=new PushDevice();device.setAppCode("ZAKAZ");device.setActiveRole("customer");
        for(String event:List.of("live","won","lost")) assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("scope","lottery","channel","lottery","event",event),device));
    }
    @Test void onlyAdminMayRevokeTicketsOrDeleteLotteries() throws Exception {
        for(String path:List.of("/api/v5/push/admin/lotteries/42/tickets/00001/revoke","/api/v5/push/admin/lotteries/42/delete")) {
            mvc.perform(post(path).header("Authorization","member-token")).andExpect(status().isUnauthorized());
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void announceToAllRequiresAdminAndDoesNotReplaceParticipantNotification() throws Exception {
        mvc.perform(post("/api/v5/push/admin/lotteries/42/notify?audience=all").header("Authorization","member-token"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(admins.requireAdmin("admin-token")).thenReturn(com.codesupreme.sifarisqrupu.model.admin.Admin.builder().id(1L).build());
        when(service.announce(42L)).thenReturn(new LotteryService.Notice(2,3,true));
        mvc.perform(post("/api/v5/push/admin/lotteries/42/notify?audience=all").header("Authorization","admin-token").accept("application/json"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.queued").value(2));
        verify(service).announce(42L);
        mvc.perform(post("/api/v5/push/admin/lotteries/42/notify").header("Authorization","admin-token"))
            .andExpect(status().isOk());
        verify(service).notifyParticipants(42L);
        mvc.perform(post("/api/v5/push/admin/lotteries/42/notify?audience=invalid").header("Authorization","admin-token"))
            .andExpect(status().isBadRequest());
    }
    @Test void announcementStatusIsAdminOnlyAndReadsInsteadOfSending() throws Exception {
        mvc.perform(get("/api/v5/push/admin/lotteries/42/notify?requestId=request-1234567890"))
            .andExpect(status().isUnauthorized());
        when(admins.requireAdmin("admin-token")).thenReturn(com.codesupreme.sifarisqrupu.model.admin.Admin.builder().id(1L).build());
        when(service.announcementStatus(42L,"request-1234567890")).thenReturn(Map.of("completed",true,"notification",new LotteryService.Notice(4,0,true)));
        mvc.perform(get("/api/v5/push/admin/lotteries/42/notify?requestId=request-1234567890").header("Authorization","admin-token").accept("application/json"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true)).andExpect(jsonPath("$.notification.queued").value(4));
        verify(service).announcementStatus(42L,"request-1234567890");
        verify(service,never()).announce(anyLong(),anyString());
    }

}

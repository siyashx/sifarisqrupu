package com.codesupreme.sifarisqrupu.advertising;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.PushAuth;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class AdvertisingControllerTest {
    @Test void ownRequestsUseAuthenticatedIdentityAndRejectInvalidCredentials() throws Exception {
        var auth=mock(PushAuth.class);var service=mock(AdvertisingService.class);
        when(auth.requireUser("member")).thenReturn(User.builder().id(7L).build());
        when(auth.requireUser("invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        when(service.mine(7L,"MOTO")).thenReturn(Map.of("items",List.of(),"serverNow",1L));
        var mvc=MockMvcBuilders.standaloneSetup(new AdvertisingController(auth,service)).build();
        mvc.perform(get("/api/v5/advertising/mine?channel=MOTO&userId=99").header("Authorization","member")).andExpect(status().isOk());
        mvc.perform(get("/api/v5/advertising/promotion/8?userId=99").header("Authorization","member")).andExpect(status().isOk());
        verify(service).promotion(7L,8L);verify(service,never()).promotion(eq(99L),any());
        verify(service).mine(7L,"MOTO");verify(service,never()).mine(eq(99L),any());
        clearInvocations(service);
        mvc.perform(get("/api/v5/advertising/mine?channel=MOTO").header("Authorization","invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v5/advertising/mine?channel=MOTO")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void onlyAdminCanEditOrDeleteAdvertising() throws Exception {
        var auth=mock(com.codesupreme.sifarisqrupu.push.AdminPushAuth.class);var service=mock(AdvertisingService.class);
        when(auth.requireAdmin("member")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var mvc=MockMvcBuilders.standaloneSetup(new AdminAdvertisingController(auth,service)).build();
        mvc.perform(put("/api/v5/push/admin/advertising/requests/4").header("Authorization","member").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v5/push/admin/advertising/requests/4").header("Authorization","member")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
        mvc.perform(put("/api/v5/push/admin/advertising/requests/4").header("Authorization","admin").contentType("application/json").content("{\"title\":\"New title\",\"version\":2}" )).andExpect(status().isOk());verify(service).editRequest(eq(4L),any());
        mvc.perform(delete("/api/v5/push/admin/advertising/requests/4").header("Authorization","admin")).andExpect(status().isOk());verify(service).deleteRequest(4L);
    }
    @Test void memberCannotCreateOrReactivateAnAdminAd() throws Exception {
        var auth=mock(com.codesupreme.sifarisqrupu.push.AdminPushAuth.class);var service=mock(AdvertisingService.class);
        when(auth.requireAdmin("member")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var mvc=MockMvcBuilders.standaloneSetup(new AdminAdvertisingController(auth,service)).build();
        for(String url:List.of("/chat","/requests/4/reactivate"))mvc.perform(post("/api/v5/push/admin/advertising"+url).header("Authorization","member").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        mvc.perform(post("/api/v5/push/admin/advertising/chat").header("Authorization","admin").contentType("application/json").content("{}" )).andExpect(status().isOk());verify(service).createAdminChat(any());
        mvc.perform(post("/api/v5/push/admin/advertising/requests/4/reactivate").header("Authorization","admin").contentType("application/json").content("{}" )).andExpect(status().isOk());verify(service).reactivate(eq(4L),any());
    }
}

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
        verify(service).mine(7L,"MOTO");verify(service,never()).mine(eq(99L),any());
        clearInvocations(service);
        mvc.perform(get("/api/v5/advertising/mine?channel=MOTO").header("Authorization","invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v5/advertising/mine?channel=MOTO")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}

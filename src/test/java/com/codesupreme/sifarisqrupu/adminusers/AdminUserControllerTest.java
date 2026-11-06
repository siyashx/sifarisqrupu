package com.codesupreme.sifarisqrupu.adminusers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import com.codesupreme.sifarisqrupu.pairing.PairingErrors;

class AdminUserControllerTest {
 @Test void requiresAdminAndReturnsOnlySmallPageFields() throws Exception {
  var auth=mock(AdminPushAuth.class); var service=mock(AdminUserService.class);
  var row=new AdminUserRow(7L,"Test","0551234567","moto_courier",List.of(),"active",false,null,false,null);
  when(service.list("","all","none","all",0,40)).thenReturn(new AdminUserService.Page(List.of(row),0,false,1,Map.of("courier",1L,"customer",0L)));
  when(auth.requireAdmin("bad")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  var mvc=MockMvcBuilders.standaloneSetup(new AdminUserController(auth,service)).setControllerAdvice(new PairingErrors()).build();
  mvc.perform(get("/api/v5/push/admin/users").header("Authorization","admin").accept("application/json"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(7))
   .andExpect(jsonPath("$.items[0].isSub").value(false)).andExpect(jsonPath("$.items[0].isSubMotoTaksi").value(false))
   .andExpect(jsonPath("$.items[0].password").doesNotExist()).andExpect(jsonPath("$.items[0].notificationHistory").doesNotExist())
   .andExpect(jsonPath("$.items[0].phoneId").doesNotExist()).andExpect(jsonPath("$.hasMore").value(false));
  mvc.perform(get("/api/v5/push/admin/users").header("Authorization","bad")).andExpect(status().isUnauthorized());
  verify(service,times(1)).list(anyString(),anyString(),anyString(),anyString(),anyInt(),anyInt());
 }
}

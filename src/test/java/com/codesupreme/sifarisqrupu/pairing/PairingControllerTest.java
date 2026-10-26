package com.codesupreme.sifarisqrupu.pairing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class PairingControllerTest {

  @Test
  void adminActionsCannotBeInvokedWithMemberCredentialsAndResultIdentityComesFromAuth()
    throws Exception {
    var auth = mock(PushAuth.class);
    when(auth.requireUser("member")).thenReturn(User.builder().id(7L).build());
    var admins = mock(AdminPushAuth.class);
    when(admins.requireAdmin(any())).thenThrow(
      new ResponseStatusException(HttpStatus.UNAUTHORIZED)
    );
    var service = mock(PairingService.class);
    when(service.detail(1L, 7L)).thenReturn(Map.of("id", 1));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(
      new PairingController(auth, service),
      new AdminPairingController(admins, service)
    )
      .setControllerAdvice(new PairingErrors())
      .build();
    mvc
      .perform(
        get("/api/v5/pairings/1?userId=99").header("Authorization", "member")
      )
      .andExpect(status().isOk());
    verify(service).detail(1L, 7L);
    for (String path : List.of(
      "/start",
      "/notify",
      "/status",
      "/delete",
      "/rounds/1/games/0/reset"
    ))
      mvc
        .perform(
          post("/api/v5/push/admin/pairings/1" + path)
            .header("Authorization", "member")
            .contentType("application/json")
            .content("{\"durationSeconds\":30}")
        )
        .andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/v5/pairings/1/rounds/1/games/0/result?userId=99")
          .header("Authorization", "member")
          .contentType("application/json")
          .content("{\"choice\":\"won\",\"revision\":0}")
      )
      .andExpect(status().isOk());
    verify(service).result(eq(1L), eq(1), eq(0), eq(7L), any(), eq(false));
  }
}

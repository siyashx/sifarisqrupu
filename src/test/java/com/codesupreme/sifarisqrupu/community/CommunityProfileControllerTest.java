package com.codesupreme.sifarisqrupu.community;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.PushAuth;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommunityProfileControllerTest {
  @Test void ticketContactProfileNeverIncludesCredentials() {
    var auth = mock(PushAuth.class);
    when(auth.requireUser("member")).thenReturn(User.builder().id(7L).name("Name")
      .phoneNumber("0709559984").password("private").build());
    var result = new CommunityProfileController(auth).me("member");
    assertEquals(Set.of("id", "name", "phoneNumber"),result.keySet());
    assertEquals("0709559984",result.get("phoneNumber"));
  }
}

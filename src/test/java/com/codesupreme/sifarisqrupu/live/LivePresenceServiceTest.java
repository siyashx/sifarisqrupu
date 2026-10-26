package com.codesupreme.sifarisqrupu.live;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.lottery.*;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.pairing.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LivePresenceServiceTest {

  @Test
  void viewersAreUniquePerAccountAndRoomAndMultipleSessionsDoNotInflateCount() {
    var users = mock(UserRepository.class);
    when(users.findById(7L)).thenReturn(
      Optional.of(
        User.builder()
          .id(7L)
          .name("Member")
          .identifyPhoto(List.of("https://private/id.jpg"))
          .build()
      )
    );
    var lotteries = mock(LotteryRepository.class);
    when(lotteries.findById(1L)).thenReturn(Optional.of(new Lottery()));
    var pairings = mock(PairingRepository.class);
    when(pairings.findById(1L)).thenReturn(Optional.of(new Pairing()));
    var service = new LivePresenceService(users, lotteries, pairings);
    assertEquals(
      1,
      service
        .heartbeat("lottery", 1L, "session-1234567890", 7L, false, false)
        .get("count")
    );
    assertEquals(
      1,
      service
        .heartbeat("lottery", 1L, "session-2222222222", 7L, false, false)
        .get("count")
    );
    assertEquals(
      1,
      service
        .heartbeat("lottery", 1L, "session-1234567890", 7L, false, true)
        .get("count")
    );
    assertEquals(0, service.list("pairing", 1L).get("count"));
    assertEquals(
      2,
      service
        .heartbeat("lottery", 1L, "admin-session-12345", 7L, true, false)
        .get("count")
    );
    assertEquals(
      1,
      service
        .heartbeat("lottery", 1L, "session-2222222222", 7L, false, true)
        .get("count")
    );
  }
}

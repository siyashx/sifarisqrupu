package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dao.chat_group.ChatGroupRepository;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushAudienceCommunityTest {
    @Test void elehberShopIncludesOfflineCustomersWithoutAWorkSubscription() {
        var users=mock(UserRepository.class);
        when(users.findAll()).thenReturn(List.of(
            User.builder().id(7L).userType("customer").online(false).build(),
            User.builder().id(8L).userType("customer").online(false).build(),
            User.builder().id(9L).isDisable(true).build()));
        var service=new PushAudienceService(users,mock(ChatGroupRepository.class),mock(NotificationMutePreferenceService.class));
        assertEquals(List.of(8L),service.select("ELEHBER","shop",7L,null,false));
        assertEquals(List.of(8L),service.select("ZAKAZ","shop",7L,null,false));
        assertTrue(service.select("ELEHBER","moto_chat",7L,null,false).isEmpty());
    }
}

package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminVerificationPushServiceTest {
    @Test void documentSaveAndMobileNotifyUseSameEventAndOnlySelectedActiveAdmins() {
        var fcm=mock(FcmPushService.class);var users=mock(UserRepository.class);var admins=mock(AdminRepository.class);
        var user=User.builder().id(9L).courierStatus("active").identifyPhoto(List.of("selfie","front","back")).build();
        when(users.findById(9L)).thenReturn(Optional.of(user));when(fcm.isEnabled()).thenReturn(true);
        when(admins.findAllById(List.of(1L,2L))).thenReturn(List.of(Admin.builder().id(1L).build(),Admin.builder().id(2L).isMutedNotifications(true).build()));
        var service=new AdminVerificationPushService(fcm,admins,users,"1,2");
        service.notifyAdmin(9L);service.notifyAdmin(9L);
        var keys=ArgumentCaptor.forClass(String.class);
        verify(fcm,times(2)).enqueue(eq("ADMIN"),eq(List.of(1L)),anyString(),anyString(),argThat(m->m.get("courierId").equals(9L) && m.get("screen").equals("UserDetails")),eq(86400),keys.capture());
        assertEquals(keys.getAllValues().get(0),keys.getAllValues().get(1));
        user.setIdentifyPhoto(List.of("new-selfie","front","back"));service.notifyAdmin(9L);
        verify(fcm,times(3)).enqueue(eq("ADMIN"),anyList(),anyString(),anyString(),anyMap(),anyInt(),keys.capture());
        assertNotEquals(keys.getAllValues().get(0),keys.getValue());
    }
    @Test void incompleteOrAcceptedApplicationsDoNotNotify() {
        var fcm=mock(FcmPushService.class);var users=mock(UserRepository.class);var admins=mock(AdminRepository.class);
        when(fcm.isEnabled()).thenReturn(true);
        var user=User.builder().id(9L).courierStatus("active").identifyPhoto(List.of("one")).build();when(users.findById(9L)).thenReturn(Optional.of(user));
        var service=new AdminVerificationPushService(fcm,admins,users,"1");assertFalse(service.notifyAdmin(9L));
        user.setIdentifyPhoto(List.of("a","b","c"));user.setCourierStatus("accept");assertFalse(service.notifyAdmin(9L));
        verify(fcm,never()).enqueue(any(),any(),any(),any(),any(),anyInt(),any());
    }
}

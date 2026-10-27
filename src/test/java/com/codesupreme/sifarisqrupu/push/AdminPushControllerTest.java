package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminPushControllerTest {
    @Test void registrationBindsAuthenticatedAdminAndDisablesDuplicateToken() {
        var auth=mock(AdminPushAuth.class);when(auth.requireAdmin("auth")).thenReturn(Admin.builder().id(5L).build());
        var devices=mock(PushDeviceRepository.class);var duplicate=new PushDevice();duplicate.setId(9L);duplicate.setEnabled(true);
        when(devices.findByAppCodeAndToken(eq("ADMIN"),anyString())).thenReturn(List.of(duplicate));
        var controller=new AdminPushController(auth,devices,mock(FcmPushService.class),mock(UserRepository.class),mock(PushAudienceService.class));
        controller.register("auth",new AdminPushController.DeviceRequest("installation-123456","token-token-token-token",true));
        var saved=ArgumentCaptor.forClass(PushDevice.class);verify(devices,times(2)).save(saved.capture());
        assertFalse(duplicate.isEnabled());assertEquals(5L,saved.getValue().getUserId());assertEquals("ADMIN",saved.getValue().getAppCode());
    }
    @Test void revokeCannotDisableAnotherAdminsDevice() {
        var auth=mock(AdminPushAuth.class);when(auth.requireAdmin("auth")).thenReturn(Admin.builder().id(5L).build());
        var devices=mock(PushDeviceRepository.class);var device=new PushDevice();device.setUserId(6L);device.setEnabled(true);
        when(devices.findByAppCodeAndInstallationId("ADMIN","installation")).thenReturn(Optional.of(device));
        var controller=new AdminPushController(auth,devices,mock(FcmPushService.class),mock(UserRepository.class),mock(PushAudienceService.class));
        controller.revoke("auth",new AdminPushController.RevokeRequest("installation"));assertTrue(device.isEnabled());verify(devices,never()).save(any());
    }
    @Test void motoChatUsesBothAppsEligibleAudiencesWithoutAClientMembershipList() {
        var auth=mock(AdminPushAuth.class);when(auth.requireAdmin("auth")).thenReturn(Admin.builder().id(5L).build());
        var fcm=mock(FcmPushService.class);when(fcm.isEnabled()).thenReturn(true);
        var audience=mock(PushAudienceService.class);
        when(audience.select("ZAKAZ","moto_chat",null,null,false)).thenReturn(List.of(7L));
        when(audience.select("ELEHBER","moto_chat",null,null,false)).thenReturn(List.of(8L));
        var controller=new AdminPushController(auth,mock(PushDeviceRepository.class),fcm,mock(UserRepository.class),audience);
        controller.send("auth",new AdminPushController.SendRequest("moto_chat",null,"Moto Taksi Çat","Mesaj",1L,"MotoTaksi","test-request-12345678"));
        for(String app:List.of("ZAKAZ","ELEHBER")) {
            verify(audience).select(app,"moto_chat",null,null,false);
            verify(fcm).enqueue(eq(app),eq(List.of("ZAKAZ".equals(app)?7L:8L)),eq("Moto Taksi Çat"),eq("Mesaj"),eq(PushAudienceService.payload(app,"moto_chat")),eq(300),anyString());
        }
        assertThrows(IllegalArgumentException.class,()->controller.send("auth",new AdminPushController.SendRequest("community",List.of(7L),"Old","Message",2L,"Community","test-request-12345679")));
    }
}

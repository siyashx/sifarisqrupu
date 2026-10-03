package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushControllerTest {
    PushAuth auth;
    PushDeviceRepository devices;
    FcmPushService fcm;
    PushAudienceService audience;
    OrderRepository orders;
    PushController controller;
    @BeforeEach void setup() {
        auth=mock(PushAuth.class);devices=mock(PushDeviceRepository.class);fcm=mock(FcmPushService.class);
        audience=mock(PushAudienceService.class);orders=mock(OrderRepository.class);
        controller=new PushController(auth,devices,fcm,audience,orders,mock(AdminVerificationPushService.class),"bridge-secret");
        when(auth.requireUser("valid")).thenReturn(User.builder().id(7L).build());when(fcm.isEnabled()).thenReturn(true);
    }
    @Test void registrationTakesOwnerFromAuthentication() {
        var request=new PushController.DeviceRequest("ELEHBER","installation-123456", "valid-fcm-token-123456789012345", "android",true);
        controller.register("valid",request);
        var captured=org.mockito.ArgumentCaptor.forClass(PushDevice.class);verify(devices).save(captured.capture());
        assertEquals(7L,captured.getValue().getUserId());assertEquals("ELEHBER",captured.getValue().getAppCode());
    }
    @Test void revokingOneAccountCannotUnregisterAnotherAccount() {
        var device=new PushDevice();device.setUserId(99L);device.setEnabled(true);
        when(devices.findByAppCodeAndInstallationId("ELEHBER","installation-123456")).thenReturn(Optional.of(device));
        controller.revoke("valid",new PushController.RevokeRequest("ELEHBER","installation-123456"));
        assertTrue(device.isEnabled());verify(devices,never()).save(any());
    }
    @Test void bridgeEndpointRejectsMissingOrWrongKey() {
        var event=new PushController.BridgeRequest("group","Title","Body","source-event");
        assertThrows(ResponseStatusException.class,()->controller.bridge(null,event));
        assertThrows(ResponseStatusException.class,()->controller.bridge("wrong",event));
        verifyNoInteractions(fcm);
    }
    @Test void bridgeMotoChatUsesBothAppAudiencesWithDifferentEventIds() {
        when(audience.select(anyString(),eq("moto_chat"),isNull(),isNull(),eq(true))).thenReturn(List.of(7L));
        controller.bridge("bridge-secret",new PushController.BridgeRequest("moto_chat","Title","Body","source-event"));
        verify(fcm).enqueue(eq("ZAKAZ"),eq(List.of(7L)),anyString(),anyString(),anyMap(),eq(300),eq("bridge-source-event-ZAKAZ"));
        verify(fcm).enqueue(eq("ELEHBER"),eq(List.of(7L)),anyString(),anyString(),anyMap(),eq(300),eq("bridge-source-event-ELEHBER"));
    }
    @Test void noticeRequiresExplicitRecipientList() {
        var request=new PushController.SendRequest("ELEHBER","notice","T","B",null,"event");
        assertThrows(IllegalArgumentException.class,()->controller.send("valid",request));
    }
    @Test void invalidPlatformFailsBeforeDatabaseWrite() {
        var request=new PushController.DeviceRequest("ELEHBER","installation-123456","valid-fcm-token-123456789012345",null,true);
        assertThrows(IllegalArgumentException.class,()->controller.register("valid",request));verify(devices,never()).save(any());
    }
}

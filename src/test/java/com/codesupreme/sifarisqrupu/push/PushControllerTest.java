package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushControllerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ZAKAZ","ELEHBER"})
    void sharedShopListingNotifiesBothAppsFromEitherSource(String source) {
        when(audience.select(anyString(),eq("shop"),eq(7L),isNull(),eq(false))).thenReturn(List.of(8L));
        when(fcm.enqueue(anyString(),anyCollection(),anyString(),anyString(),anyMap(),anyInt(),anyString())).thenReturn(2);
        var result=controller.send("valid",new PushController.SendRequest(source,"shop","New listing","Body",null,"listing-id"));
        assertEquals(4,result.get("queued"));
        for(String target:List.of("ZAKAZ","ELEHBER")) {
            verify(fcm).enqueue(eq(target),eq(List.of(8L)),eq("New listing"),eq("Body"),argThat(d->"shop".equals(d.get("channel"))),eq(300),eq("user-7-listing-id-"+target));
        }
    }
    @Test void elehberAlsoRequiresReplacementOfRejectedToken() {
        var d=new PushDevice();d.setId(10L);d.rejectToken("invalid-fcm-token-123456789012345");
        when(devices.findByAppCodeAndInstallationId("ELEHBER","installation-123456")).thenReturn(Optional.of(d));
        var result=controller.register("valid",new PushController.DeviceRequest("ELEHBER","installation-123456",
            "invalid-fcm-token-123456789012345","android",true,"customer"));
        assertEquals(true,result.get("tokenRefreshRequired"));assertFalse(d.isEnabled());
    }
    @Test void cachedRejectedZakazTokenCannotBeEnabledAgain() {
        var device=new PushDevice();device.setId(10L);device.setToken("invalid-fcm-token-123456789012345");
        device.rejectToken(device.getToken());device.setEnabled(false);
        when(devices.findByAppCodeAndInstallationId("ZAKAZ","installation-123456")).thenReturn(Optional.of(device));
        var result=controller.register("valid",new PushController.DeviceRequest("ZAKAZ","installation-123456",
            device.getToken(),"android",true,"customer"));
        assertEquals(true,result.get("tokenRefreshRequired"));assertEquals(false,result.get("registered"));
        assertFalse(device.isEnabled());
        var replacement=controller.register("valid",new PushController.DeviceRequest("ZAKAZ","installation-123456",
            "replacement-token-123456789012345","android",true,"customer"));
        assertEquals(false,replacement.get("tokenRefreshRequired"));assertEquals(true,replacement.get("registered"));
        assertTrue(device.isEnabled());assertNull(device.getRejectedTokenHash());
    }

    @Test void rejectedTokenCannotBeReusedUnderAnotherInstallation() {
        var previous=new PushDevice();previous.setId(10L);previous.rejectToken("invalid-fcm-token-123456789012345");
        when(devices.findByAppCodeAndToken("ZAKAZ","invalid-fcm-token-123456789012345")).thenReturn(List.of(previous));
        var result=controller.register("valid",new PushController.DeviceRequest("ZAKAZ","installation-new-1234",
            "invalid-fcm-token-123456789012345","android",true,"customer"));
        assertEquals(true,result.get("tokenRefreshRequired"));
    }
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
    @Test void registrationStoresActiveRolePerInstallationWithoutChangingSharedAccount() {
        controller.register("valid",new PushController.DeviceRequest("ELEHBER","installation-123456",
            "valid-fcm-token-123456789012345","ios",true,"customer"));
        var captured=org.mockito.ArgumentCaptor.forClass(PushDevice.class);verify(devices).save(captured.capture());
        assertEquals("customer",captured.getValue().getActiveRole());
        assertEquals(7L,captured.getValue().getUserId());
    }
    @Test void zakazRegistrationAlsoStoresLocalRoleWithoutChangingCourierCapability() {
        var user=User.builder().id(7L).userType("moto_courier").build();
        when(auth.requireUser("valid")).thenReturn(user);
        controller.register("valid",new PushController.DeviceRequest("ZAKAZ","installation-123456",
            "valid-fcm-token-123456789012345","ios",true,"customer"));
        var captured=org.mockito.ArgumentCaptor.forClass(PushDevice.class);verify(devices).save(captured.capture());
        assertEquals("customer",captured.getValue().getActiveRole());
        assertEquals("moto_courier",user.getUserType());
    }
    @Test void invalidActiveRoleIsRejected() {
        assertThrows(IllegalArgumentException.class,()->controller.register("valid",new PushController.DeviceRequest(
            "ELEHBER","installation-123456","valid-fcm-token-123456789012345","ios",true,"admin")));
        verify(devices,never()).save(any());
    }
    @Test void oldClientClearsInstallationRoleInsteadOfInheritingAnotherAccountRole() {
        var device=new PushDevice();device.setActiveRole("customer");device.setUserId(99L);
        when(devices.findByAppCodeAndInstallationId("ELEHBER","installation-123456")).thenReturn(Optional.of(device));
        controller.register("valid",new PushController.DeviceRequest("ELEHBER","installation-123456",
            "valid-fcm-token-123456789012345","android",true));
        assertNull(device.getActiveRole());assertEquals(7L,device.getUserId());
    }

}

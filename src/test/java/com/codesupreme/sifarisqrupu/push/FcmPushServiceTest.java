package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FcmPushServiceTest {
    PushDeviceRepository devices;
    PushDeliveryRepository deliveries;
    UserRepository users;
    PushDeliveryPolicy policy;
    FcmPushService service;
    FirebaseMessaging messaging;
    PushDevice device;
    @BeforeEach void setup() {
        devices=mock(PushDeviceRepository.class);deliveries=mock(PushDeliveryRepository.class);users=mock(UserRepository.class);
        policy=mock(PushDeliveryPolicy.class);messaging=mock(FirebaseMessaging.class);
        service=new FcmPushService(devices,deliveries,users,mock(com.codesupreme.sifarisqrupu.dao.admin.AdminRepository.class),new ObjectMapper(),policy,true,"test-project");
        ReflectionTestUtils.setField(service,"messaging",messaging);
        device=new PushDevice();device.setId(10L);device.setUserId(7L);device.setAppCode("ELEHBER");device.setEnabled(true);
        device.setToken("test-device-token");device.setPlatform("android");
        when(devices.findByAppCodeAndUserIdInAndEnabledTrue(eq("ELEHBER"),any())).thenReturn(List.of(device));
        when(devices.findById(10L)).thenReturn(Optional.of(device));
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).isDisable(false).build()));
        when(policy.allows(anyMap(),anyLong())).thenReturn(true);
    }
    PushDelivery pending() {
        PushDelivery d=new PushDelivery();d.setId(1L);d.setAppCode("ELEHBER");d.setUserId(7L);d.setDeviceId(10L);
        d.setState("PENDING");d.setExpiresAt(System.currentTimeMillis()+60000);d.setPayload("{\"event\":\"new_order\"}");
        when(deliveries.findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(eq("PENDING"),anyLong())).thenReturn(List.of(d));
        return d;
    }
    @Test void queueContainsAppAndUserBoundPayload() throws Exception {
        assertEquals(1,service.enqueue("ELEHBER",List.of(7L),"Title","Body",Map.of("orderId",42),60,"event-1"));
        var captor=org.mockito.ArgumentCaptor.forClass(PushDelivery.class);verify(deliveries).save(captor.capture());
        PushDelivery item=captor.getValue();
        var payload=new ObjectMapper().readTree(item.getPayload());
        assertEquals("7",payload.get("recipientUserId").asText());assertEquals("42",payload.get("orderId").asText());
        assertEquals("event-1",payload.get("eventId").asText());assertEquals("ELEHBER",item.getAppCode());
        assertTrue(item.getExpiresAt()>System.currentTimeMillis());
    }
    @Test void duplicateEventDoesNotQueueTwice() {
        when(deliveries.existsByEventIdAndDeviceId("same-event",10L)).thenReturn(true);
        assertEquals(0,service.enqueue("ELEHBER",List.of(7L),"T","B",Map.of(),60,"same-event"));
        verify(deliveries,never()).save(any());
    }
    @Test void disabledProviderDoesNotSendOrQueue() {
        var off=new FcmPushService(devices,deliveries,users,mock(com.codesupreme.sifarisqrupu.dao.admin.AdminRepository.class),new ObjectMapper(),policy,false,"test");
        assertEquals(0,off.enqueue("ELEHBER",List.of(7L),"T","B",Map.of(),60,null));off.deliverDue();
        verifyNoInteractions(messaging,deliveries);
    }
    @Test void expiredMessagesNeverReachFirebase() {
        var d=pending();d.setExpiresAt(1L);service.deliverDue();assertEquals("EXPIRED",d.getState());verifyNoInteractions(messaging);
    }
    @Test void accountSwitchInvalidatesOldQueuedMessage() {
        var d=pending();device.setUserId(99L);service.deliverDue();assertEquals("EXPIRED",d.getState());verifyNoInteractions(messaging);
    }
    @Test void logoutInvalidatesQueuedMessage() {
        var d=pending();device.setEnabled(false);service.deliverDue();assertEquals("EXPIRED",d.getState());verifyNoInteractions(messaging);
    }
    @Test void staleOfferDoesNotSoundAfterOrderTaken() {
        var d=pending();when(policy.allows(anyMap(),anyLong())).thenReturn(false);
        service.deliverDue();assertEquals("EXPIRED",d.getState());verifyNoInteractions(messaging);
    }
    @Test void successfulDeliveryIsRecorded() throws Exception {
        var d=pending();when(messaging.send(any(Message.class))).thenReturn("message-id");
        service.deliverDue();assertEquals("SENT",d.getState());verify(messaging).send(any(Message.class));
    }
    @Test void temporaryFailureRetainsDeliveryWithBackoff() throws Exception {
        var d=pending();var error=mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(error);
        service.deliverDue();assertEquals("PENDING",d.getState());assertEquals(1,d.getAttempts());assertTrue(d.getNextAttemptAt()>System.currentTimeMillis());
    }
    @Test void invalidTokenIsDisabled() throws Exception {
        var d=pending();var error=mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);
        service.deliverDue();assertEquals("FAILED",d.getState());assertFalse(device.isEnabled());
    }
    @Test void tokenRotationDuringSendRetriesWithoutDisablingNewToken() throws Exception {
        var d=pending();var current=new PushDevice();current.setId(10L);current.setUserId(7L);current.setAppCode("ELEHBER");
        current.setEnabled(true);current.setToken("rotated-token");
        when(devices.findById(10L)).thenReturn(Optional.of(device),Optional.of(current));
        var error=mock(FirebaseMessagingException.class);when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);
        service.deliverDue();assertEquals("PENDING",d.getState());assertTrue(current.isEnabled());
        assertTrue(d.getNextAttemptAt()>System.currentTimeMillis());verify(devices,never()).save(any());
    }
    @Test void otherAppDevicesCannotBeSelected() {
        service.enqueue("ZAKAZ",List.of(7L),"T","B",Map.of(),60,null);
        verify(devices).findByAppCodeAndUserIdInAndEnabledTrue("ZAKAZ",List.of(7L));
        verify(deliveries,never()).save(any());
    }
    @Test void adminQueueUsesAdminIdentityAndPackageWithoutCourierAccount() throws Exception {
        var admins=mock(com.codesupreme.sifarisqrupu.dao.admin.AdminRepository.class);
        ReflectionTestUtils.setField(service,"admins",admins);
        device.setAppCode("ADMIN");device.setUserId(1L);
        when(admins.findById(1L)).thenReturn(Optional.of(com.codesupreme.sifarisqrupu.model.admin.Admin.builder().id(1L).build()));
        when(devices.findByAppCodeAndUserIdInAndEnabledTrue(eq("ADMIN"),any())).thenReturn(List.of(device));
        service.enqueue("ADMIN",List.of(1L),"Request","New documents",Map.of("courierId",9),60,"admin-event");
        var saved=org.mockito.ArgumentCaptor.forClass(PushDelivery.class);verify(deliveries).save(saved.capture());
        var item=saved.getValue();var data=new ObjectMapper().readTree(item.getPayload());
        assertEquals("1",data.get("recipientAdminId").asText());assertFalse(data.has("recipientUserId"));
        when(deliveries.findTop100ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(eq("PENDING"),anyLong())).thenReturn(List.of(item));
        service.deliverDue();assertEquals("SENT",item.getState());verify(users,never()).findById(1L);
        var message=org.mockito.ArgumentCaptor.forClass(Message.class);verify(messaging).send(message.capture());
        Object android=ReflectionTestUtils.getField(message.getValue(),"androidConfig");
        assertEquals("com.biglikuryer.sifarisqrupuadmin",ReflectionTestUtils.getField(android,"restrictedPackageName"));
    }
}

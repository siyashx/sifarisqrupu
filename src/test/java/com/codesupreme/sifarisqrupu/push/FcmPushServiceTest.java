package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @ParameterizedTest
    @CsvSource({
        "ZAKAZ, group, mototaxi, chime.caf",
        "ZAKAZ, shop, , chime.caf",
        "ZAKAZ, moto_chat, , notifysound.caf",
        "ZAKAZ, , mototaxi, notifysound.caf",
        "ZAKAZ, , mototaxi_chat, notifysound.caf",
        "ZAKAZ, , , chime.caf",
        "ZAKAZ, unknown, , chime.caf",
        "ELEHBER, moto_chat, , notifysound.caf",
        "ELEHBER, group, , notifysound.caf",
        "ELEHBER, shop, , chime.caf",
        "ELEHBER, , mototaxi, notifysound.caf",
        "ELEHBER, , mototaxi_chat, notifysound.caf",
        "ELEHBER, , , notifysound.caf",
        "ELEHBER, unknown, , notifysound.caf"
    })
    void iosAlertUsesBundledSoundForItsSection(String app, String channel, String scope, String sound) throws Exception {
        device.setAppCode(app);device.setPlatform("ios");
        var d=pending();d.setAppCode(app);
        Map<String,String> data=new HashMap<>(Map.of("title","Title","body","Body"));
        if(channel!=null) data.put("channel",channel);
        if(scope!=null) data.put("scope",scope);
        d.setPayload(new ObjectMapper().writeValueAsString(data));
        service.deliverDue();
        var captor=org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        Object config=ReflectionTestUtils.getField(captor.getValue(),"apnsConfig");
        var payload=(Map<?,?>)ReflectionTestUtils.getField(config,"payload");
        var aps=(Map<?,?>)payload.get("aps");
        assertEquals(sound,aps.get("sound"));
        assertEquals("Title",ReflectionTestUtils.getField(aps.get("alert"),"title"));
        assertEquals("Body",ReflectionTestUtils.getField(aps.get("alert"),"body"));
        assertEquals("SENT",d.getState());
    }
    @Test void silentIosPushDoesNotAcquireSoundOrAlert() throws Exception {
        device.setAppCode("ZAKAZ");device.setPlatform("ios");
        var d=pending();d.setAppCode("ZAKAZ");
        service.deliverDue();
        var captor=org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        Object config=ReflectionTestUtils.getField(captor.getValue(),"apnsConfig");
        var payload=(Map<?,?>)ReflectionTestUtils.getField(config,"payload");
        var aps=(Map<?,?>)payload.get("aps");
        assertFalse(aps.containsKey("sound"));assertFalse(aps.containsKey("alert"));
    }
    @Test void androidPushKeepsClientSideSoundSelection() throws Exception {
        device.setAppCode("ZAKAZ");
        var d=pending();d.setAppCode("ZAKAZ");
        d.setPayload("{\"title\":\"Title\",\"body\":\"Body\",\"channel\":\"moto_chat\"}");
        service.deliverDue();
        var captor=org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        assertNull(ReflectionTestUtils.getField(captor.getValue(),"apnsConfig"));
        assertNull(ReflectionTestUtils.getField(captor.getValue(),"notification"));
    }
    @Test void temporaryFailureRetainsDeliveryWithBackoff() throws Exception {
        var d=pending();var error=mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(error);
        service.deliverDue();assertEquals("PENDING",d.getState());assertEquals(1,d.getAttempts());assertTrue(d.getNextAttemptAt()>System.currentTimeMillis());
    }
    @Test void iosFailureLogIdentifiesDeviceAndReasonWithoutPrivateData() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(FcmPushService.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            device.setPlatform("ios");
            var d=pending();d.setPayload("{\"title\":\"Private title\",\"body\":\"Private message\"}");
            var error=mock(FirebaseMessagingException.class);
            when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
            when(error.getMessage()).thenReturn("Invalid APNs token: test-device-token; Private title; Private message\nBasic secret-auth-value");
            var response=mock(com.google.firebase.IncomingHttpResponse.class);
            when(error.getHttpResponse()).thenReturn(response);
            when(response.getContent()).thenReturn("{\"error\":{\"details\":[{\"apnsError\":{\"reason\":\"BadDeviceToken\"}},{\"fieldViolations\":[{\"field\":\"message.token\",\"description\":\"Invalid test-device-token\"}]}]}}");
            when(messaging.send(any(Message.class))).thenThrow(error);
            service.deliverDue();
            String message=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .filter(line->line.startsWith("FCM delivery failed.")).findFirst().orElseThrow();
            assertTrue(message.contains("platform=ios"));assertTrue(message.contains("deviceId=10"));
            assertTrue(message.contains("code=INVALID_ARGUMENT"));assertTrue(message.contains("Invalid APNs token"));
            assertTrue(message.contains("apnsReason=BadDeviceToken"));assertTrue(message.contains("field=message.token"));
            assertFalse(message.contains(device.getToken()));assertFalse(message.contains("Private title"));
            assertFalse(message.contains("Private message"));assertFalse(message.contains("secret-auth-value"));
            assertFalse(message.contains("\n"));assertEquals("FAILED",d.getState());assertTrue(device.isEnabled());
        } finally {
            logger.detachAppender(appender);appender.stop();service.close();
        }
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

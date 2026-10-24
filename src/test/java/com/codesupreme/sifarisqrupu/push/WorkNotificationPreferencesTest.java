package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.notification_mute.NotificationMuteStatusDto;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkNotificationPreferencesTest {
    private final OrderRepository orders=mock(OrderRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final NotificationMutePreferenceService mutes=mock(NotificationMutePreferenceService.class);
    private final PushDeliveryPolicy policy=new PushDeliveryPolicy(orders,users,mutes);
    private final User user=User.builder().id(7L).online(true).build();
    private final NotificationMuteStatusDto zakaz=NotificationMuteStatusDto.builder().muted(false).build();
    private final NotificationMuteStatusDto elehber=NotificationMuteStatusDto.builder().muted(false).build();

    WorkNotificationPreferencesTest() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(mutes.getStatus(7L,"ZAKAZ","ORDER_GROUP_PUSH")).thenReturn(zakaz);
        when(mutes.getStatus(7L,"ELEHBER","MOTOTAXI_CHAT")).thenReturn(elehber);
    }
    @Test void latestZakazMuteBlocksQueuedGroupAndMotoChatAlerts() {
        for(var data:List.of(Map.of("channel","group","groupId","0"),Map.of("channel","moto_chat","scope","mototaxi_chat","groupId","1"))) {
            zakaz.setMuted(false);assertTrue(policy.allows(data,7L,"ZAKAZ"));
            // Preference changed after the notification was prepared.
            zakaz.setMuted(true);assertFalse(policy.allows(data,7L,"ZAKAZ"));
        }
    }
    @Test void latestElehberOfflineBlocksChatAndAnOtherwiseValidCourierOffer() {
        when(orders.findById(42L)).thenReturn(Optional.of(Order.builder().id(42L).status("no_courier")
            .activeOfferExpirations(Map.of(7L,new Date(System.currentTimeMillis()+60000))).build()));
        for(var data:List.of(Map.of("channel","moto_chat","scope","mototaxi_chat"),
            Map.of("scope","mototaxi","event","new_order","orderId","42"))) {
            user.setOnline(true);assertTrue(policy.allows(data,7L,"ELEHBER"));
            user.setOnline(false);assertFalse(policy.allows(data,7L,"ELEHBER"));
        }
    }
    @Test void muteAndOfflinePreferencesStaySeparateBetweenApps() {
        var data=Map.of("scope","mototaxi_chat","channel","moto_chat");
        zakaz.setMuted(true);assertFalse(policy.allows(data,7L,"ZAKAZ"));
        assertTrue(policy.allows(data,7L,"ELEHBER"));
        zakaz.setMuted(false);user.setOnline(false);
        assertTrue(policy.allows(data,7L,"ZAKAZ"));assertFalse(policy.allows(data,7L,"ELEHBER"));
    }
    @Test void chatMuteIsIndependentFromDirectRideOffers() {
        elehber.setMuted(true);
        assertFalse(policy.allows(Map.of("scope","mototaxi_chat"),7L,"ELEHBER"));
        when(orders.findById(42L)).thenReturn(Optional.of(Order.builder().id(42L).status("no_courier")
            .activeOfferExpirations(Map.of(7L,new Date(System.currentTimeMillis()+60000))).build()));
        assertTrue(policy.allows(Map.of("scope","mototaxi","event","new_order","orderId","42"),7L,"ELEHBER"));
    }
    @Test void lotteryShopPersonalStatusesAndControlsBypassWorkPreferences() {
        user.setOnline(false);zakaz.setMuted(true);elehber.setMuted(true);
        for(String app:List.of("ZAKAZ","ELEHBER")) {
            for(String event:List.of("created","live","won","lost")) {
                assertTrue(policy.allows(Map.of("scope","lottery","channel","lottery","event",event),7L,app));
            }
            assertTrue(policy.allows(Map.of("channel","shop","groupId","3"),7L,app));
            for(String event:List.of("accepted","pickedUp","completed","customer_cancelled","order_unavailable")) {
                assertTrue(policy.allows(Map.of("scope","mototaxi","channel","moto_chat","event",event),7L,app));
            }
            assertTrue(policy.allows(PushAudienceService.payload(app,"notice").entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().toString())),7L,app));
        }
        verifyNoInteractions(users,mutes,orders);
    }
}

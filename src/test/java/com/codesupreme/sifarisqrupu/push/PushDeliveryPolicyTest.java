package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.model.order.Order;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PushDeliveryPolicyTest {
    @Test void requiresLiveCourierOfferAndOpenOrder() {
        var repo=mock(OrderRepository.class);var policy=new PushDeliveryPolicy(repo);
        var order=Order.builder().id(42L).status("no_courier").activeOfferExpirations(Map.of(7L,new Date(System.currentTimeMillis()+60000))).build();
        when(repo.findById(42L)).thenReturn(Optional.of(order));
        var data=Map.of("scope","mototaxi","event","new_order","orderId","42");
        assertTrue(policy.allows(data,7L));assertFalse(policy.allows(data,8L));
        order.setStatus("to_customer");assertFalse(policy.allows(data,7L));
        order.setStatus("no_courier");order.setIsDisable(true);assertFalse(policy.allows(data,7L));
    }
    @Test void controlsStillDeliverForClosedOrders() {
        var repo=mock(OrderRepository.class);var policy=new PushDeliveryPolicy(repo);
        assertTrue(policy.allows(Map.of("scope","mototaxi","event","customer_cancelled"),7L));
        verifyNoInteractions(repo);
    }
    @Test void ownOfferIsRejectedEvenForACourierCapableAccountAndLegacyOffer() {
        var repo=mock(OrderRepository.class);var policy=new PushDeliveryPolicy(repo);
        var order=Order.builder().id(42L).customerId(7L).status("no_courier")
            .activeOfferExpirations(Map.of(7L,new Date(System.currentTimeMillis()+60000)))
            .offeredCourierId(7L).offerExpiresAt(new Date(System.currentTimeMillis()+60000)).build();
        when(repo.findById(42L)).thenReturn(Optional.of(order));
        assertFalse(policy.allows(Map.of("scope","mototaxi","event","new_order","orderId","42"),7L));
    }
    @Test void customerRoleBlocksChatAndOffersButKeepsOwnOrderStatusAndControls() {
        var device=new PushDevice();device.setAppCode("ELEHBER");device.setActiveRole("customer");
        assertFalse(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi_chat"),device));
        assertFalse(PushDeliveryPolicy.allowsDevice(Map.of("channel","moto_chat"),device));
        assertFalse(PushDeliveryPolicy.allowsDevice(Map.of("groupId","1"),device));
        assertFalse(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi","event","new_order"),device));
        for(String event:List.of("accepted","courierCancelled","pickedUp","completed","customer_cancelled","order_unavailable")) {
            assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi","channel","moto_chat","event",event),device),event);
        }
        device.setActiveRole("courier");
        assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi_chat"),device));
        assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi","event","new_order"),device));
    }
    @Test void sushiDoesNotDeliverToElehberAndZakazWorkAlertsRequireNonCustomerRole() {
        var device=new PushDevice();device.setAppCode("ELEHBER");device.setActiveRole("courier");
        assertFalse(PushDeliveryPolicy.allowsDevice(Map.of("groupId","0"),device));
        device.setAppCode("ZAKAZ");device.setActiveRole("customer");
        for(var data:List.of(Map.of("groupId","0"),Map.of("channel","group"),Map.of("scope","group"),
            Map.of("scope","mototaxi_chat"),Map.of("channel","moto_chat"),Map.of("groupId","1"),
            Map.of("scope","mototaxi","event","new_order"))) {
            assertFalse(PushDeliveryPolicy.allowsDevice(data,device),data.toString());
            device.setActiveRole("courier");
            assertTrue(PushDeliveryPolicy.allowsDevice(data,device),data.toString());
            device.setActiveRole("customer");
        }
        assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("channel","shop","groupId","3"),device));
        assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("scope","mototaxi","channel","moto_chat","event","accepted"),device));
        device.setActiveRole(null);
        assertTrue(PushDeliveryPolicy.allowsDevice(Map.of("channel","group"),device));
    }

}

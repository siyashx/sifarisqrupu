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
}

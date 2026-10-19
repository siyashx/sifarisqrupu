package com.codesupreme.sifarisqrupu.service.impl.order;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiCourierPushService;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiPricingService;
import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotoTaxiOrderDispatchServiceTest {
    OrderRepository orders=mock(OrderRepository.class);
    UserRepository users=mock(UserRepository.class);
    MotoTaxiCourierPushService push=mock(MotoTaxiCourierPushService.class);
    MotoTaxiPricingService pricing=mock(MotoTaxiPricingService.class);
    PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    MotoTaxiOrderDispatchService service;
    Order order;
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(pricing.getDispatchRadiusKm()).thenReturn(50.0);
        service=new MotoTaxiOrderDispatchService(orders,users,new ModelMapper(),push,pricing,transactions,300,60);
        order=Order.builder().id(42L).customerId(7L).status("no_courier").orderType("ride")
            .fromAddress(List.of("Baku","40.4","49.8")).createdAt(new Date())
            .activeOfferExpirations(new LinkedHashMap<>()).cancelledCourierIds(new ArrayList<>()).build();
        when(orders.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
    }
    User courier(long id) {
        return User.builder().id(id).userType("moto_courier").courierStatus("accept")
            .online(true).currentLocation(List.of("40.4","49.8")).build();
    }
    @Test void ownCourierCapableAccountIsExcludedWhileAnotherNearbyCourierGetsOffer() {
        when(users.findAll()).thenReturn(List.of(courier(7),courier(8)));
        service.processOrder(42L);
        assertEquals(Set.of(8L),order.getActiveOfferExpirations().keySet());
        verify(push).sendNewOrderOffer(eq(8L),any(),eq(42L),eq("ride"));
        verify(push,never()).sendNewOrderOffer(eq(7L),any(),any(),any());
    }
    @Test void legacySelfOfferCannotBeAccepted() {
        order.getActiveOfferExpirations().put(7L,new Date(System.currentTimeMillis()+60000));
        assertThrows(ResponseStatusException.class,()->service.acceptOrder(42L,7L));
        assertNull(order.getCourierId());
    }
}

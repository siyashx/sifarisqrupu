package com.codesupreme.sifarisqrupu.scheduler;

import com.codesupreme.sifarisqrupu.service.impl.order.OrderServiceImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderScheduler {

    private final OrderServiceImpl orderService;

    public OrderScheduler(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedRate = 60000)
    public void disableExpiredNoCourierOrders() {
        orderService.disableNoCourierOrdersAfterTenMinutes();
    }
}
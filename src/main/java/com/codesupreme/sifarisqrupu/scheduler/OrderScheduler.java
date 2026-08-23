package com.codesupreme.sifarisqrupu.scheduler;

import com.codesupreme.sifarisqrupu.service.impl.order.MotoTaxiOrderDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderScheduler {

    private final MotoTaxiOrderDispatchService dispatchService;

    public OrderScheduler(MotoTaxiOrderDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    // Frequent retries are intentional: an order stays open even if no courier is
    // online at creation time, and a courier who comes online later should receive
    // the offer quickly. Search itself still has the hard five-minute deadline.
    @Scheduled(fixedDelayString = "${mototaxi.dispatch.scheduler-delay-ms:1000}")
    public void processMotoTaxiDispatchQueue() {
        dispatchService.processAllOpenOrders();
    }
}

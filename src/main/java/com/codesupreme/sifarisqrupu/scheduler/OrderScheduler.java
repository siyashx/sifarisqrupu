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

    // Frequent retries are intentional: the first courier is offered immediately
    // and the dispatch service can fan out to the next nearest courier every
    // configured interval while the five-minute search deadline remains in force.
    @Scheduled(fixedDelayString = "${mototaxi.dispatch.scheduler-delay-ms:1000}")
    public void processMotoTaxiDispatchQueue() {
        dispatchService.processAllOpenOrders();
    }
}

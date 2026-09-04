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

    // Frequent retries are intentional: all currently eligible couriers inside
    // the configured radius are offered immediately, while newly eligible couriers
    // can still join during the existing search deadline.
    @Scheduled(fixedDelayString = "${mototaxi.dispatch.scheduler-delay-ms:1000}")
    public void processMotoTaxiDispatchQueue() {
        dispatchService.processAllOpenOrders();
    }
}

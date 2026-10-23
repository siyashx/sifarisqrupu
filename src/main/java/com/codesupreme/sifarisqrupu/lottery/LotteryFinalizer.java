package com.codesupreme.sifarisqrupu.lottery;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.LoggerFactory;
@Component
public class LotteryFinalizer {
    private final LotteryRepository repository;
    private final LotteryService service;
    public LotteryFinalizer(LotteryRepository repository,LotteryService service){this.repository=repository;this.service=service;}
    @Scheduled(fixedDelay=1000)
    public void finishDue() {
        for(var l:repository.findByStatusAndEndsAtLessThanEqual("DRAWING",System.currentTimeMillis())) {
            try {service.finish(l.getId());}
            catch(RuntimeException error){LoggerFactory.getLogger(getClass()).error("Lottery {} finalization pending retry",l.getId());}
        }
    }
}

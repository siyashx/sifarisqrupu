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
            catch(RuntimeException error){
                // Transaction rolls back both completion and result pushes;
                // next scheduled pass retries the same lottery atomically.
                Throwable cause=error;while(cause.getCause()!=null && cause.getCause()!=cause) cause=cause.getCause();
                LoggerFactory.getLogger(getClass()).error("Lottery {} finalization pending retry. cause={}",l.getId(),cause.getClass().getSimpleName());
            }
        }
    }
}

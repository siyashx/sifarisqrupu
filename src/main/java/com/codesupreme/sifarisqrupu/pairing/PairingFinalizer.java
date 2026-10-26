package com.codesupreme.sifarisqrupu.pairing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PairingFinalizer {

  private final PairingRepository repository;
  private final PairingService service;

  public PairingFinalizer(
    PairingRepository repository,
    PairingService service
  ) {
    this.repository = repository;
    this.service = service;
  }

  @Scheduled(fixedDelay = 1000)
  public void finish() {
    for (var p : repository.findByStatusAndDeletedFalse("DRAWING")) {
      try {
        service.finishAnimation(p.getId());
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Pairing {} completion pending retry: {}",
          p.getId(),
          e.getClass().getSimpleName()
        );
      }
    }
  }
}

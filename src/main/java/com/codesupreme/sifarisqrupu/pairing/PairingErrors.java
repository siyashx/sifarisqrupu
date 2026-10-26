package com.codesupreme.sifarisqrupu.pairing;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(
  basePackages = {
    "com.codesupreme.sifarisqrupu.pairing",
    "com.codesupreme.sifarisqrupu.live",
  }
)
public class PairingErrors {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> rejected(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode()).body(
      Map.of(
        "message",
        error.getReason() == null ? "Əməliyyat alınmadı." : error.getReason()
      )
    );
  }
}

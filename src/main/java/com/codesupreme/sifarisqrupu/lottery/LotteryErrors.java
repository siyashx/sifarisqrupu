package com.codesupreme.sifarisqrupu.lottery;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.util.Map;
@RestControllerAdvice(assignableTypes={LotteryController.class,AdminLotteryController.class})
public class LotteryErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason()==null ? "Əməliyyat alınmadı.":error.getReason()));
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception error) {return ResponseEntity.badRequest().body(Map.of("message","Məlumat formatı düzgün deyil."));}
}

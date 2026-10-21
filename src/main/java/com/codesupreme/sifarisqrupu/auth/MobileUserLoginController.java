package com.codesupreme.sifarisqrupu.auth;

import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Single-account mobile login; the existing endpoint remains compatible. */
@RestController
@RequestMapping("/api/v5/login/user/mobile")
public class MobileUserLoginController {
    private final MobileUserLoginService service;

    public MobileUserLoginController(MobileUserLoginService service) {
        this.service = service;
    }

    public record Request(String phoneNumber, String password, String phoneId) {}

    @PostMapping
    public ResponseEntity<?> login(@RequestBody Request request) {
        try {
            UserDto user = service.login(request.phoneNumber(), request.password(), request.phoneId());
            return ResponseEntity.ok(user);
        } catch (MobileUserLoginService.Rejected error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
        }
    }
}

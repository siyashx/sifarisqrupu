package com.codesupreme.sifarisqrupu.api.notification_mute.controller;

import com.codesupreme.sifarisqrupu.dto.notification_mute.NotificationMuteStatusDto;
import com.codesupreme.sifarisqrupu.dto.notification_mute.NotificationMuteUpdateDto;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v5/notification_mute")
public class NotificationMutePreferenceController {

    private final NotificationMutePreferenceService service;

    public NotificationMutePreferenceController(NotificationMutePreferenceService service) {
        this.service = service;
    }

    @GetMapping("/{appCode}/{scope}/{userId}")
    public ResponseEntity<?> getStatus(
            @PathVariable String appCode,
            @PathVariable String scope,
            @PathVariable Long userId
    ) {
        try {
            return ResponseEntity.ok(service.getStatus(userId, appCode, scope));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @PutMapping("/{appCode}/{scope}/{userId}")
    public ResponseEntity<?> setStatus(
            @PathVariable String appCode,
            @PathVariable String scope,
            @PathVariable Long userId,
            @RequestBody NotificationMuteUpdateDto dto
    ) {
        try {
            return ResponseEntity.ok(service.setStatus(userId, appCode, scope, dto.getMuted()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping("/{appCode}/{scope}/muted-user-ids")
    public ResponseEntity<?> getMutedUserIds(
            @PathVariable String appCode,
            @PathVariable String scope
    ) {
        try {
            final List<Long> ids = service.getEffectiveMutedUserIds(appCode, scope);
            return ResponseEntity.ok(ids);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }
}

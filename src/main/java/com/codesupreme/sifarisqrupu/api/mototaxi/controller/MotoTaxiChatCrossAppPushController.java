package com.codesupreme.sifarisqrupu.api.mototaxi.controller;

import com.codesupreme.sifarisqrupu.dto.mototaxi.MotoTaxiCrossAppPushRequest;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiChatCrossAppPushService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v5/mototaxi/chat")
public class MotoTaxiChatCrossAppPushController {

    private final MotoTaxiChatCrossAppPushService pushService;

    public MotoTaxiChatCrossAppPushController(MotoTaxiChatCrossAppPushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/cross-app-push")
    public ResponseEntity<?> sendCrossAppPush(@RequestBody MotoTaxiCrossAppPushRequest request) {
        try {
            return ResponseEntity.ok(pushService.sendToOppositeApp(
                    request.getSourceApp(),
                    request.getSenderUserId(),
                    request.getUsername(),
                    request.getMessage()
            ));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }
}

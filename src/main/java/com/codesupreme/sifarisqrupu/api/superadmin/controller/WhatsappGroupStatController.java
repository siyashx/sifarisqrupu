package com.codesupreme.sifarisqrupu.api.superadmin.controller;

import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.GroupStatIncrementRequest;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupDailyStat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v5/whatsapp-group-stats")
public class WhatsappGroupStatController {

    private static final ZoneId BAKU_ZONE =
            ZoneId.of("Asia/Baku");

    private final WhatsappGroupDailyStatRepository repository;

    public WhatsappGroupStatController(
            WhatsappGroupDailyStatRepository repository
    ) {
        this.repository = repository;
    }

    /*
     * JS server hər qəbul edilən sifariş üçün
     * bu endpoint-i çağıracaq.
     */
    @PostMapping("/increment")
    public ResponseEntity<?> increment(
            @RequestBody GroupStatIncrementRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body("Request body boşdur");
        }

        if (request.getInstanceName() == null ||
                request.getInstanceName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("instanceName vacibdir");
        }

        if (request.getGroupJid() == null ||
                request.getGroupJid().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("groupJid vacibdir");
        }

        if (request.getGroupName() == null ||
                request.getGroupName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("groupName vacibdir");
        }

        LocalDate today = LocalDate.now(BAKU_ZONE);

        repository.increment(
                request.getInstanceName().trim(),
                request.getGroupJid().trim(),
                request.getGroupName().trim(),
                today
        );

        return ResponseEntity.ok().build();
    }

    /*
     * React Native bu endpoint-dən bu günün
     * qrup statistikasını alacaq.
     */
    @GetMapping("/today")
    public ResponseEntity<List<WhatsappGroupDailyStat>>
    getTodayStatistics() {

        LocalDate today = LocalDate.now(BAKU_ZONE);

        List<WhatsappGroupDailyStat> statistics =
                repository.findByStatDateOrderByOrderCountDesc(today);

        return ResponseEntity.ok(statistics);
    }
}
package com.codesupreme.sifarisqrupu.api.superadmin.controller;

import com.codesupreme.sifarisqrupu.dao.superadmin.GroupOrderStatProjection;
import com.codesupreme.sifarisqrupu.dao.superadmin.UserMessageStatProjection;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupDailyStatRepository;
import com.codesupreme.sifarisqrupu.dao.superadmin.WhatsappGroupUserDailyStatRepository;
import com.codesupreme.sifarisqrupu.dto.superadmin.GroupOrderStatResponse;
import com.codesupreme.sifarisqrupu.dto.superadmin.GroupStatIncrementRequest;
import com.codesupreme.sifarisqrupu.dto.superadmin.UserMessageStatResponse;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupDailyStat;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappGroupUserDailyStat;
import com.codesupreme.sifarisqrupu.service.impl.superadmin.WhatsappGroupStatService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v5/whatsapp-group-stats")
public class WhatsappGroupStatController {

    private static final ZoneId BAKU_ZONE =
            ZoneId.of("Asia/Baku");

    private final WhatsappGroupStatService service;
    private final WhatsappGroupDailyStatRepository groupRepository;
    private final WhatsappGroupUserDailyStatRepository userRepository;

    public WhatsappGroupStatController(
            WhatsappGroupStatService service,
            WhatsappGroupDailyStatRepository groupRepository,
            WhatsappGroupUserDailyStatRepository userRepository
    ) {
        this.service = service;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /*
     * Node.js hər qəbul edilən yeni sifariş üçün çağırır.
     *
     * phone müəyyən edilibsə:
     * - qrupun orderCount dəyəri artırılır;
     * - həmin nömrənin həmin qrup üzrə messageCount dəyəri artırılır.
     *
     * phone yoxdursa yalnız qrup sayı artırılır.
     */
    @PostMapping("/increment")
    public ResponseEntity<?> increment(
            @RequestBody GroupStatIncrementRequest request
    ) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body("Request body boşdur");
        }

        if (
                request.getInstanceName() == null ||
                request.getInstanceName().isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("instanceName vacibdir");
        }

        if (
                request.getGroupJid() == null ||
                request.getGroupJid().isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("groupJid vacibdir");
        }

        if (
                request.getGroupName() == null ||
                request.getGroupName().isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("groupName vacibdir");
        }

        service.increment(request);

        return ResponseEntity.ok().build();
    }

    /*
     * Bu günün qruplar üzrə ümumi sifariş sayı.
     */
    @GetMapping("/today")
    public ResponseEntity<List<WhatsappGroupDailyStat>>
    getTodayStatistics() {

        LocalDate today =
                LocalDate.now(BAKU_ZONE);

        List<WhatsappGroupDailyStat> statistics =
                groupRepository
                        .findByStatDateOrderByOrderCountDesc(
                                today
                        );

        return ResponseEntity.ok(statistics);
    }


    /*
     * İstənilən tarix aralığı üzrə qrupların sifariş sayı.
     * from və to daxil olmaqla hesablanır.
     *
     * Nümunə:
     * /range?from=2026-09-01&to=2026-09-09
     */
    @GetMapping("/range")
    public ResponseEntity<?>
    getRangeStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        ResponseEntity<String> invalid =
                validateRange(from, to);

        if (invalid != null) {
            return invalid;
        }

        List<GroupOrderStatProjection> rows =
                groupRepository
                        .findGroupStatisticsBetween(
                                from,
                                to
                        );

        List<GroupOrderStatResponse> response =
                rows.stream()
                        .map(row ->
                                new GroupOrderStatResponse(
                                        row.getInstanceName(),
                                        row.getGroupJid(),
                                        row.getGroupName(),
                                        safeLong(
                                                row.getOrderCount()
                                        )
                                )
                        )
                        .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /*
     * Seçilmiş qrup üzrə tarix aralığında
     * ən çox mesaj yazan nömrələr.
     */
    @GetMapping("/range/users/group")
    public ResponseEntity<?>
    getRangeGroupUserStatistics(
            @RequestParam String instanceName,
            @RequestParam String groupJid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (
                instanceName == null ||
                instanceName.isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("instanceName vacibdir");
        }

        if (
                groupJid == null ||
                groupJid.isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("groupJid vacibdir");
        }

        ResponseEntity<String> invalid =
                validateRange(from, to);

        if (invalid != null) {
            return invalid;
        }

        List<UserMessageStatProjection> rows =
                userRepository
                        .findGroupUserStatisticsBetween(
                                from,
                                to,
                                instanceName.trim(),
                                groupJid.trim()
                        );

        return ResponseEntity.ok(
                toUserResponse(rows)
        );
    }

    /*
     * Bütün qruplar üzrə tarix aralığında
     * nömrələrin cəmi mesaj sayı.
     */
    @GetMapping("/range/users/overall")
    public ResponseEntity<?>
    getRangeOverallUserStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        ResponseEntity<String> invalid =
                validateRange(from, to);

        if (invalid != null) {
            return invalid;
        }

        List<UserMessageStatProjection> rows =
                userRepository
                        .findOverallUserStatisticsBetween(
                                from,
                                to
                        );

        return ResponseEntity.ok(
                toUserResponse(rows)
        );
    }

    /*
     * Seçilmiş qrup üzrə bu gün ən çox mesaj yazan nömrələr.
     *
     * Nümunə:
     * /today/users/group?instanceName=sifarisqrupu&groupJid=1203...@g.us
     */
    @GetMapping("/today/users/group")
    public ResponseEntity<?>
    getTodayGroupUserStatistics(
            @RequestParam String instanceName,
            @RequestParam String groupJid
    ) {
        if (
                instanceName == null ||
                instanceName.isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("instanceName vacibdir");
        }

        if (
                groupJid == null ||
                groupJid.isBlank()
        ) {
            return ResponseEntity.badRequest()
                    .body("groupJid vacibdir");
        }

        LocalDate today =
                LocalDate.now(BAKU_ZONE);

        List<WhatsappGroupUserDailyStat> rows =
                userRepository
                        .findByStatDateAndInstanceNameAndGroupJidOrderByMessageCountDesc(
                                today,
                                instanceName.trim(),
                                groupJid.trim()
                        );

        List<UserMessageStatResponse> response =
                rows.stream()
                        .map(row ->
                                new UserMessageStatResponse(
                                        row.getPhone(),
                                        safeLong(
                                                row.getMessageCount()
                                        )
                                )
                        )
                        .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /*
     * Bütün WhatsApp qrupları üzrə nömrələrin
     * bugünkü cəmi mesaj sayı.
     */
    @GetMapping("/today/users/overall")
    public ResponseEntity<List<UserMessageStatResponse>>
    getTodayOverallUserStatistics() {

        LocalDate today =
                LocalDate.now(BAKU_ZONE);

        List<UserMessageStatProjection> rows =
                userRepository
                        .findOverallUserStatistics(
                                today
                        );

        List<UserMessageStatResponse> response =
                rows.stream()
                        .map(row ->
                                new UserMessageStatResponse(
                                        row.getPhone(),
                                        safeLong(
                                                row.getMessageCount()
                                        )
                                )
                        )
                        .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private List<UserMessageStatResponse>
    toUserResponse(
            List<UserMessageStatProjection> rows
    ) {
        return rows.stream()
                .map(row ->
                        new UserMessageStatResponse(
                                row.getPhone(),
                                safeLong(
                                        row.getMessageCount()
                                )
                        )
                )
                .collect(Collectors.toList());
    }

    private ResponseEntity<String>
    validateRange(
            LocalDate from,
            LocalDate to
    ) {
        if (from == null || to == null) {
            return ResponseEntity.badRequest()
                    .body("from və to vacibdir");
        }

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest()
                    .body("from tarixinin to tarixindən sonra olması olmaz");
        }

        return null;
    }

    private static long safeLong(
            Number value
    ) {
        return value == null
                ? 0L
                : value.longValue();
    }
}

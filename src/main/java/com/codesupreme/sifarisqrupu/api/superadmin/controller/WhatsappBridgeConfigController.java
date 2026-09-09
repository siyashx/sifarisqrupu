package com.codesupreme.sifarisqrupu.api.superadmin.controller;

import com.codesupreme.sifarisqrupu.dto.superadmin.WhatsappBridgeBlockedPhoneRequest;
import com.codesupreme.sifarisqrupu.dto.superadmin.WhatsappBridgeGroupRequest;
import com.codesupreme.sifarisqrupu.dto.superadmin.WhatsappBridgeSyncRequest;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeBlockedPhone;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeGroup;
import com.codesupreme.sifarisqrupu.model.superadmin.WhatsappBridgeInstance;
import com.codesupreme.sifarisqrupu.service.impl.superadmin.WhatsappBridgeConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v5/whatsapp-bridge")
public class WhatsappBridgeConfigController {

    private final WhatsappBridgeConfigService service;

    public WhatsappBridgeConfigController(WhatsappBridgeConfigService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestBody WhatsappBridgeSyncRequest request) {
        service.sync(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/instances")
    public ResponseEntity<List<WhatsappBridgeInstance>> instances() {
        return ResponseEntity.ok(service.getInstances());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<WhatsappBridgeGroup>> groups(
            @RequestParam String instanceName,
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        return ResponseEntity.ok(service.getGroups(instanceName, enabledOnly));
    }

    @GetMapping("/discover-groups")
    public ResponseEntity<?> discoverGroups(@RequestParam String instanceName) {
        try {
            return ResponseEntity.ok(service.discoverEvolutionGroups(instanceName));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        } catch (IllegalStateException error) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error.getMessage());
        }
    }

    @PostMapping("/groups")
    public ResponseEntity<?> addGroup(@RequestBody WhatsappBridgeGroupRequest request) {
        try {
            return ResponseEntity.ok(service.addOrEnableGroup(request));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @PutMapping("/groups/{id}/enabled")
    public ResponseEntity<?> setGroupEnabled(
            @PathVariable Long id,
            @RequestParam boolean value
    ) {
        try {
            return ResponseEntity.ok(service.setGroupEnabled(id, value));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<?> removeGroup(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.removeGroupAndStatistics(id));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @GetMapping("/blocked-phones")
    public ResponseEntity<List<WhatsappBridgeBlockedPhone>> blockedPhones(
            @RequestParam String instanceName,
            @RequestParam String groupJid
    ) {
        return ResponseEntity.ok(service.getBlockedPhones(instanceName, groupJid));
    }

    @PostMapping("/blocked-phones")
    public ResponseEntity<?> blockPhone(
            @RequestBody WhatsappBridgeBlockedPhoneRequest request
    ) {
        try {
            return ResponseEntity.ok(service.blockPhone(request));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @DeleteMapping("/blocked-phones/{id}")
    public ResponseEntity<?> unblockPhone(@PathVariable Long id) {
        try {
            service.unblockPhone(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    /*
     * wa-bridge bu endpoint-i qısa interval ilə oxuyur.
     * Yalnız enabled qruplar mesaj axınına buraxılır və blok qaydaları
     * instance + groupJid + phone üzrə tətbiq olunur.
     */
    @GetMapping("/runtime")
    public ResponseEntity<Map<String, Object>> runtime() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("groups", service.getRuntimeGroups());
        response.put("blockedPhones", service.getRuntimeBlockedPhones());
        return ResponseEntity.ok(response);
    }
}

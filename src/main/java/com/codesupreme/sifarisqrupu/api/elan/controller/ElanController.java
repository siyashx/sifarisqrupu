package com.codesupreme.sifarisqrupu.api.elan.controller;

import com.codesupreme.sifarisqrupu.dto.elan.ElanDto;
import com.codesupreme.sifarisqrupu.service.impl.elan.ElanServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/elan")
public class ElanController {

    private final ElanServiceImpl service;

    public ElanController(ElanServiceImpl service) {
        this.service = service;
    }

    // Bütün elanləri əldə et
    @GetMapping
    public ResponseEntity<List<ElanDto>> getAllElans() {
        List<ElanDto> all = service.getAllElans();
        return ResponseEntity.ok(all);
    }

    // ID-ə görə elan əldə et
    @GetMapping("/{elanId}")
    public ResponseEntity<ElanDto> getElanById(@PathVariable("elanId") Long id) {
        ElanDto elan = service.getElanById(id);
        if (elan != null) {
            return ResponseEntity.ok(elan);
        }
        return ResponseEntity.notFound().build();
    }

    // Yeni elan əlavə et
    @PostMapping
    public ResponseEntity<ElanDto> saveElan(@RequestBody ElanDto dto) {
        ElanDto created = service.saveElan(dto);
        return ResponseEntity.ok(created);
    }

    // Elanı yenilə
    @PutMapping("/{elanId}")
    public ResponseEntity<?> updateElan(
            @PathVariable("elanId") Long id,
            @RequestBody ElanDto elanDto) {
        ElanDto updatedElan = service.updateElan(id, elanDto);
        if (updatedElan != null) {
            return ResponseEntity.ok(updatedElan);
        }
        return ResponseEntity.notFound().build();
    }

    // Elanı sil
    @DeleteMapping("/{elanId}")
    public ResponseEntity<String> deleteElan(@PathVariable("elanId") Long id) {
        Boolean deleted = service.deleteElan(id);
        if (deleted) {
            return ResponseEntity.ok("Elan deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}

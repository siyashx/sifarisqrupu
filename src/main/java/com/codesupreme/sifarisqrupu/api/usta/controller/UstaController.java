package com.codesupreme.sifarisqrupu.api.usta.controller;

import com.codesupreme.sifarisqrupu.dto.usta.UstaDto;
import com.codesupreme.sifarisqrupu.service.impl.usta.UstaServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/usta")
public class UstaController {

    private final UstaServiceImpl service;

    public UstaController(UstaServiceImpl service) {
        this.service = service;
    }

    // Bütün ustaləri əldə et
    @GetMapping
    public ResponseEntity<List<UstaDto>> getAllUstas() {
        List<UstaDto> all = service.getAllUstas();
        return ResponseEntity.ok(all);
    }

    // ID-ə görə usta əldə et
    @GetMapping("/{ustaId}")
    public ResponseEntity<UstaDto> getUstaById(@PathVariable("ustaId") Long id) {
        UstaDto usta = service.getUstaById(id);
        if (usta != null) {
            return ResponseEntity.ok(usta);
        }
        return ResponseEntity.notFound().build();
    }

    // Yeni usta əlavə et
    @PostMapping
    public ResponseEntity<UstaDto> saveUsta(@RequestBody UstaDto dto) {
        UstaDto created = service.saveUsta(dto);
        return ResponseEntity.ok(created);
    }

    // Ustaı yenilə
    @PutMapping("/{ustaId}")
    public ResponseEntity<?> updateUsta(
            @PathVariable("ustaId") Long id,
            @RequestBody UstaDto ustaDto) {
        UstaDto updatedUsta = service.updateUsta(id, ustaDto);
        if (updatedUsta != null) {
            return ResponseEntity.ok(updatedUsta);
        }
        return ResponseEntity.notFound().build();
    }

    // Ustaı sil
    @DeleteMapping("/{ustaId}")
    public ResponseEntity<String> deleteUsta(@PathVariable("ustaId") Long id) {
        Boolean deleted = service.deleteUsta(id);
        if (deleted) {
            return ResponseEntity.ok("Usta deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}

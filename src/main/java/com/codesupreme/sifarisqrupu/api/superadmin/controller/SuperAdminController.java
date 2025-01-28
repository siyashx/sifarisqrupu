package com.codesupreme.sifarisqrupu.api.superadmin.controller;

import com.codesupreme.sifarisqrupu.dto.superadmin.SuperAdminDto;
import com.codesupreme.sifarisqrupu.service.impl.superadmin.SuperAdminServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v3/superAdmin")
public class SuperAdminController {

    private final SuperAdminServiceImpl service;


    public SuperAdminController(SuperAdminServiceImpl service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SuperAdminDto>> getAllSuperAdmin() {
        List<SuperAdminDto> all = service.getAllSuperAdmin();
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{superAdminId}")
    public ResponseEntity<SuperAdminDto> getSuperAdminById(@PathVariable("detId") Long id) {
        SuperAdminDto det = service.getSuperAdminById(id);
        if (det != null) {
            return ResponseEntity.ok(det);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<SuperAdminDto> saveSuperAdmin(@RequestBody SuperAdminDto dto) {
        SuperAdminDto created = service.saveSuperAdmin(dto);
        return ResponseEntity.ok(created);
    }

    // Update
    @PutMapping("/{superAdminId}")
    public ResponseEntity<?> updateSuperAdmin(
            @PathVariable("superAdminId") Long id,
            @RequestBody SuperAdminDto superAdminDto) {
        SuperAdminDto updatedSuperAdmin = service.updateSuperAdmin(id, superAdminDto);
        if (updatedSuperAdmin != null) {
            return ResponseEntity.ok(updatedSuperAdmin);
        }
        return ResponseEntity.notFound().build();
    }
}


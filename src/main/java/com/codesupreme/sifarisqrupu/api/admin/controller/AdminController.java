package com.codesupreme.sifarisqrupu.api.admin.controller;


import com.codesupreme.sifarisqrupu.dto.admin.AdminDto;
import com.codesupreme.sifarisqrupu.service.impl.admin.AdminServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/admin")
public class AdminController {

    private final AdminServiceImpl adminServiceImpl;

    public AdminController(AdminServiceImpl adminServiceImpl) {
        this.adminServiceImpl = adminServiceImpl;
    }

    // List
    @GetMapping
    public List<AdminDto> getAllAdmins() {
        return adminServiceImpl.getAllAdmins();
    }

    // Id
    @GetMapping("/{adminId}")
    public ResponseEntity<?> getAdminById(@PathVariable("adminId") Long adminId) {
        AdminDto adminDto = adminServiceImpl.getAdminById(adminId);
        if (adminDto != null) {
            return ResponseEntity.ok(adminDto);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Admin doesn't exist with given id..");
    }

    // Update
    @PutMapping("/{adminId}")
    public ResponseEntity<?> updateAdmin(
            @PathVariable("adminId") Long id,
            @RequestBody AdminDto adminDto) {
        AdminDto updatedAdmin = adminServiceImpl.updateAdmin(id, adminDto);
        if (updatedAdmin != null) {
            return ResponseEntity.ok(updatedAdmin);
        }
        return ResponseEntity.notFound().build();
    }

    // Create
    @PostMapping
    public ResponseEntity<?> createAdmin(@RequestBody AdminDto adminDto) {
        return adminServiceImpl.createAdmin(adminDto);
    }
}

package com.codesupreme.sifarisqrupu.api.admin.controller;

import com.codesupreme.sifarisqrupu.dto.admin.AdminDto;
import com.codesupreme.sifarisqrupu.model.LoginRequest;
import com.codesupreme.sifarisqrupu.service.inter.admin.AdminServiceInter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v5/login/admin")
public class AdminLoginController {
    private final AdminServiceInter adminServiceInter;

    public AdminLoginController(AdminServiceInter adminServiceInter) {
        this.adminServiceInter = adminServiceInter;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();
        String password = loginRequest.getPassword();

        AdminDto adminDto = adminServiceInter.findAdminByPhoneNumber(phoneNumber);

        if (adminDto != null && adminDto.getPassword().equals(password)) {
            if (!adminDto.getIsDisable()) {
                return ResponseEntity.ok(adminDto);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("This user is inactive or deleted");
            }
        }


        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid email, username or password");
    }
}

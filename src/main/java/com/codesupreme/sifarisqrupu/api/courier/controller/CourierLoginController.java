package com.codesupreme.sifarisqrupu.api.courier.controller;

import com.codesupreme.sifarisqrupu.dto.courier.CourierDto;
import com.codesupreme.sifarisqrupu.model.LoginRequest;
import com.codesupreme.sifarisqrupu.service.inter.courier.CourierServiceInter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v5/login/courier")
public class CourierLoginController {
    private final CourierServiceInter courierServiceInter;

    public CourierLoginController(CourierServiceInter courierServiceInter) {
        this.courierServiceInter = courierServiceInter;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();
        String password = loginRequest.getPassword();

        CourierDto courierDto = courierServiceInter.findCourierByPhoneNumber(phoneNumber);

        if (courierDto != null && courierDto.getPassword().equals(password)) {
            if (!courierDto.getIsDisable()) {
                return ResponseEntity.ok(courierDto);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("This user is inactive or deleted");
            }
        }


        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid email, username or password");
    }
}

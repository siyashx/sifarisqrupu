package com.codesupreme.sifarisqrupu.api.user.controller;

import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.model.LoginRequest;
import com.codesupreme.sifarisqrupu.service.impl.user.UserServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v5/login/user")
public class UserLoginController {

    private final UserServiceImpl userServiceImpl;

    public UserLoginController(UserServiceImpl userServiceImpl) {
        this.userServiceImpl = userServiceImpl;
    }

    // Login
    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();
        String password = loginRequest.getPassword();

        UserDto userDto = userServiceImpl.findUserByPhoneNumber(phoneNumber);

        if (userDto != null && userDto.getPassword().equals(password)) {
            if (!userDto.getIsDisable()) {
                return ResponseEntity.ok(userDto);
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("This user is inactive or deleted");
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                "Invalid username, email or password");
    }
}

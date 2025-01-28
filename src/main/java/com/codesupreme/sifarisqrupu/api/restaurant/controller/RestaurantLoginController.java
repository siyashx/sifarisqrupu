package com.codesupreme.sifarisqrupu.api.restaurant.controller;

import com.codesupreme.sifarisqrupu.dto.restaurant.RestaurantDto;
import com.codesupreme.sifarisqrupu.model.LoginRequest;
import com.codesupreme.sifarisqrupu.service.impl.restaurant.RestaurantServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v5/login/restaurant")
public class RestaurantLoginController {

    private final RestaurantServiceImpl restaurantServiceImpl;

    public RestaurantLoginController(RestaurantServiceImpl restaurantServiceImpl) {
        this.restaurantServiceImpl = restaurantServiceImpl;
    }

    // Login
    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();
        String password = loginRequest.getPassword();

        RestaurantDto restaurantDto = restaurantServiceImpl.findRestaurantByPhoneNumber(phoneNumber);

        if (restaurantDto != null && restaurantDto.getPassword().equals(password)) {
            if (!restaurantDto.getIsDisable()) {
                return ResponseEntity.ok(restaurantDto);
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("This user is inactive or deleted");
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                "Invalid username, email or password");
    }
}

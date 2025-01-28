package com.codesupreme.sifarisqrupu.api.restaurant.controller;

import com.codesupreme.sifarisqrupu.dto.restaurant.RestaurantDto;
import com.codesupreme.sifarisqrupu.service.impl.restaurant.RestaurantServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/restaurant")
public class RestaurantController {

    private final RestaurantServiceImpl restaurantServiceImpl;

    public RestaurantController(RestaurantServiceImpl restaurantServiceImpl) {
        this.restaurantServiceImpl = restaurantServiceImpl;
    }

    // List
    @GetMapping
    public List<RestaurantDto> getAllRestaurants() {
        return restaurantServiceImpl.getAllRestaurants();
    }

    // Id
    @GetMapping("/{restaurantId}")
    public ResponseEntity<?> getRestaurantById(@PathVariable("restaurantId") Long restaurantId) {
        RestaurantDto restaurantDto = restaurantServiceImpl.getRestaurantById(restaurantId);
        if (restaurantDto != null) {
            return ResponseEntity.ok(restaurantDto);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Restaurant doesn't exist with given id..");
    }

    // Update
    @PutMapping("/{restaurantId}")
    public ResponseEntity<?> updateRestaurant(
            @PathVariable("restaurantId") Long id,
            @RequestBody RestaurantDto restaurantDto
    ) {
        RestaurantDto updatedRestaurant = restaurantServiceImpl.updateRestaurant(id, restaurantDto);
        if (updatedRestaurant != null) {
            return ResponseEntity.ok(updatedRestaurant);
        }
        return ResponseEntity.notFound().build();
    }

    // Delete
    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<?> deleteRestaurantById(@PathVariable("restaurantId") Long restaurantId) {
        restaurantServiceImpl.deleteRestaurantById(restaurantId);
        return ResponseEntity.ok().build();
    }

    // Create
    @PostMapping
    public ResponseEntity<RestaurantDto> createRestaurant(@RequestBody RestaurantDto restaurantDto) {
        return restaurantServiceImpl.createRestaurant(restaurantDto);
    }
}

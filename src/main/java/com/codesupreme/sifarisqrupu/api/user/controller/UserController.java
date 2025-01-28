package com.codesupreme.sifarisqrupu.api.user.controller;

import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.service.impl.user.UserServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/user")
public class UserController {

    private final UserServiceImpl userServiceImpl;

    public UserController(UserServiceImpl userServiceImpl) {
        this.userServiceImpl = userServiceImpl;
    }

    // List
    @GetMapping
    public List<UserDto> getAllUsers() {
        return userServiceImpl.getAllUsers();
    }

    // Id
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable("userId") Long userId) {
        UserDto userDto = userServiceImpl.getUserById(userId);
        if (userDto != null) {
            return ResponseEntity.ok(userDto);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User doesn't exist with given id..");
    }

    // Update
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable("userId") Long id,
            @RequestBody UserDto userDto
    ) {
        UserDto updatedUser = userServiceImpl.updateUser(id, userDto);
        if (updatedUser != null) {
            return ResponseEntity.ok(updatedUser);
        }
        return ResponseEntity.notFound().build();
    }

    // Delete
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUserById(@PathVariable("userId") Long userId) {
        userServiceImpl.deleteUserById(userId);
        return ResponseEntity.ok().build();
    }

    // Create
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        return userServiceImpl.createUser(userDto);
    }
}

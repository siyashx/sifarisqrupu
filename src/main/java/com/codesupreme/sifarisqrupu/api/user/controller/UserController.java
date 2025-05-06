package com.codesupreme.sifarisqrupu.api.user.controller;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.user.UserServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v5/user")
public class UserController {

    private final UserServiceImpl userServiceImpl;
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository, UserServiceImpl userServiceImpl) {
        this.userServiceImpl = userServiceImpl;
        this.userRepository = userRepository;
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

    @GetMapping("/by-location-url")
    public ResponseEntity<User> getUserByLocationUrl(@RequestParam String url) {
        User user = userRepository.findByLocationUrl(url);
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
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

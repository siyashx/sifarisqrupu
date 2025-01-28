package com.codesupreme.sifarisqrupu.service.inter.user;

import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface UserServiceInter {

    List<UserDto> getAllUsers();
    UserDto getUserById(Long id);
    ResponseEntity<UserDto> createUser(UserDto userDto);
    UserDto updateUser(Long id, UserDto userDto);
    void deleteUserById(Long id);
    Boolean isUserPhoneNumberTaken(String phoneNumber);
    UserDto findUserByPhoneNumber(String phoneNumber);
}

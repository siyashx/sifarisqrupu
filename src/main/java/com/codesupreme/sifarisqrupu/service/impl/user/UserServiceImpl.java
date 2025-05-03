package com.codesupreme.sifarisqrupu.service.impl.user;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.inter.user.UserServiceInter;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserServiceInter {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    public UserServiceImpl(UserRepository userRepository, ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public List<UserDto> getAllUsers() {
        List<UserDto> listDto = new ArrayList<>();

        List<User> listEntity = userRepository.findAll();

        for (User entity : listEntity) {
            UserDto dto = modelMapper.map(entity, UserDto.class);
            listDto.add(dto);
        }
        return listDto;
    }

    @Override
    public UserDto getUserById(Long id) {
        User entity = userRepository.findById(id).orElse(null);
        if (entity != null) {
            return modelMapper.map(entity, UserDto.class);
        }
        return null;
    }

    @Override
    public ResponseEntity<UserDto> createUser(UserDto userDto) {
        User userEntity = modelMapper.map(userDto, User.class);
        if (isUserPhoneNumberTaken(userEntity.getPhoneNumber())) {
            return ResponseEntity.badRequest().build();
        }
        userEntity.setIsDisable(false);
        User savedUser = userRepository.save(userEntity);
        return ResponseEntity.ok(modelMapper.map(savedUser, UserDto.class));

    }

    @Override
    public UserDto updateUser(Long id, UserDto userDto) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (userDto.getOneSignal() != null) {
                user.setOneSignal(userDto.getOneSignal());
            }

            if (userDto.getUserType() != null) {
                user.setUserType(userDto.getUserType());
            }

            if (userDto.getName() != null) {
                user.setName(userDto.getName());
            }

            if (userDto.getPhoneNumber() != null) {
                user.setPhoneNumber(userDto.getPhoneNumber());
            }

            if (userDto.getNotificationHistory() != null) {
                user.setNotificationHistory(userDto.getNotificationHistory());
            }

            if (userDto.getIsMutedNotifications() != null) {
                user.setIsMutedNotifications(userDto.getIsMutedNotifications());
            }

            if (userDto.getPassword() != null) {
                user.setPassword(userDto.getPassword());
            }

            if (userDto.getOrderCount() != null) {
                user.setOrderCount(userDto.getOrderCount());
            }

            if (userDto.getLocation() != null) {
                user.setLocation(userDto.getLocation());
            }

            if (userDto.getLocationUrl() != null) {
                user.setLocationUrl(userDto.getLocationUrl());
            }


            if (userDto.getIdentifyPhoto() != null) {
                user.setIdentifyPhoto(userDto.getIdentifyPhoto());
            }

            if (userDto.getCourierStatus() != null) {
                user.setCourierStatus(userDto.getCourierStatus());
            }

            if (userDto.getCustomerStatus() != null) {
                user.setCustomerStatus(userDto.getCustomerStatus());
            }

            if (userDto.getOnline() != null) {
                user.setOnline(userDto.getOnline());
            }

            if (userDto.getActiveDeviceId() != null) {
                user.setActiveDeviceId(userDto.getActiveDeviceId());
            }

            if (userDto.getExpiryDate() != null) {
                user.setExpiryDate(userDto.getExpiryDate());
            }

            if (userDto.getCreatedDate() != null) {
                user.setCreatedDate(userDto.getCreatedDate());
            }

            if (userDto.getIsDisable() != null) {
                user.setIsDisable(userDto.getIsDisable());
            }

            user = userRepository.save(user);
            return modelMapper.map(user, UserDto.class);


        }
        return null;
    }

    @Override
    public void deleteUserById(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public Boolean isUserPhoneNumberTaken(String phoneNumber) {
        return userRepository.findUserByPhoneNumber(phoneNumber) != null;
    }

    @Override
    public UserDto findUserByPhoneNumber(String phoneNumber) {
        User user = userRepository.findUserByPhoneNumber(phoneNumber);
        if (user != null) {
            return modelMapper.map(user, UserDto.class);
        }
        return null;
    }

}

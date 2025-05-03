package com.codesupreme.sifarisqrupu.api.location.controller;

import com.codesupreme.sifarisqrupu.dto.location.LocationMessage;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.service.impl.user.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Arrays;

@Controller
public class LocationController {

    @Autowired
    private UserServiceImpl userService;

    @MessageMapping("/location") // Kuryerdən gələn WebSocket mesajı
    @SendTo("/topic/location")   // Bütün dinləyicilərə yayımlanır
    public LocationMessage updateLocation(LocationMessage message) {
        UserDto user = userService.getUserById(message.getUserId());

        if (user != null) {
            user.setLocation(Arrays.asList(
                    String.valueOf(message.getLatitude()),
                    String.valueOf(message.getLongitude())
            ));

            userService.updateUser(user.getId(), user);
        }

        return message;
    }
}

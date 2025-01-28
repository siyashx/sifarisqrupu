package com.codesupreme.sifarisqrupu.api.notification.controller;


import com.codesupreme.sifarisqrupu.dto.notification.NotificationDto;
import com.codesupreme.sifarisqrupu.service.impl.notification.NotificationImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/notification")
public class NotificationController {
    
    private final NotificationImpl service;
    public NotificationController(NotificationImpl service) {this.service = service;}

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getAllNotification() {
        List<NotificationDto> all = service.getAllNotification();
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDto> getNotificationById(@PathVariable("notificationId") Long id) {
        NotificationDto det = service.getNotificationById(id);
        if (det != null) {
            return ResponseEntity.ok(det);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<NotificationDto> saveNotification(@RequestBody NotificationDto dto) {
        NotificationDto created = service.saveNotification(dto);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<String> deleteNotification(@PathVariable("notificationId") Long id) {
        Boolean deleted = service.deleteNotification(id);
        if (deleted) {
            return ResponseEntity.ok("Admin notification deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}

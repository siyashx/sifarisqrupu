package com.codesupreme.sifarisqrupu.service.impl.notification;

import com.codesupreme.sifarisqrupu.dao.notification.NotificationRepository;
import com.codesupreme.sifarisqrupu.dto.chat.ChatDto;
import com.codesupreme.sifarisqrupu.dto.notification.NotificationDto;
import com.codesupreme.sifarisqrupu.model.chat.Chat;
import com.codesupreme.sifarisqrupu.model.notification.Notification;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationImpl {

    private final NotificationRepository notificationRepository;
    private final ModelMapper modelMapper;

    public NotificationImpl(NotificationRepository notificationRepository, ModelMapper modelMapper) {
        this.notificationRepository = notificationRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<NotificationDto> getAllNotification() {
        List<Notification> list = notificationRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, NotificationDto.class))
                .toList();
    }

    //ById
    public NotificationDto getNotificationById(Long id) {
        Optional<Notification> optional = notificationRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, NotificationDto.class)).orElse(null);
    }

    //Save
    public NotificationDto saveNotification(NotificationDto dto) {
        Notification det = modelMapper.map(dto, Notification.class);
        det = notificationRepository.save(det);
        return modelMapper.map(det, NotificationDto.class);
    }

    //Update
    public NotificationDto updateNotification(Long notificationId, NotificationDto notificationDto) {
        Optional<Notification> optional = notificationRepository.findById(notificationId);
        if (optional.isPresent()) {
            Notification notification = optional.get();


            if (notificationDto.getChatId() != null) {
                notification.setChatId(notificationDto.getChatId());
            }

            if (notificationDto.getUserId() != null) {
                notification.setUserId(notificationDto.getUserId());
            }

            if (notificationDto.getCourierId() != null) {
                notification.setCourierId(notificationDto.getCourierId());
            }

            if (notificationDto.getMessage() != null) {
                notificationDto.setMessage(notificationDto.getMessage());
            }

            if (notificationDto.getCreatedAt() != null) {
                notificationDto.setCreatedAt(notificationDto.getCreatedAt());
            }

            notification = notificationRepository.save(notification);

            return modelMapper.map(notification, NotificationDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteNotification(Long id) {
        Optional<Notification> optional = notificationRepository.findById(id);
        if (optional.isPresent()) {
            Notification det = optional.get();
            notificationRepository.delete(det);
            return true;
        }
        return false;
    }


}



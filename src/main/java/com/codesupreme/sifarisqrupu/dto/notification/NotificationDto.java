package com.codesupreme.sifarisqrupu.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class NotificationDto {

    private Long id;
    private String chatId;
    private String userId;
    private String courierId;
    private String message;
    private String createdAt;

}

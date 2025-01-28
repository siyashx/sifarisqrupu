package com.codesupreme.sifarisqrupu.dto.chat;

import lombok.Data;

@Data
public class ForwardedMessage {
    private Long userId;
    private String userType;
    private String username;
    private String message;
}

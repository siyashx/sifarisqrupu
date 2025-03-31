package com.codesupreme.sifarisqrupu.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ChatDto {

    private Long id;
    private Long userId;
    private String username;
    private List<String> isSeenIds;
    private String userType;
    private String message;
    private Boolean isWebsite;
    private String timestamp;
    private Boolean isCompleted;
}

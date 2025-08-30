package com.codesupreme.sifarisqrupu.model.chat;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "chat")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String groupId;
    private String username;
    private List<String> isSeenIds;
    private String messageType;
    private String imageUrl;
    private String audioUrl;
    private Boolean isReply;
    private Long replyUserId;
    private String replyMessage;
    private String userType;
    private String message;
    private Boolean isWebsite;
    private String timestamp;
    private Boolean isCompleted;
}


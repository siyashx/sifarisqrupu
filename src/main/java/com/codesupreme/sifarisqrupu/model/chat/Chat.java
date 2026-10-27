package com.codesupreme.sifarisqrupu.model.chat;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "chat", indexes = @Index(name = "idx_chat_group_id_id", columnList = "group_id,id"))
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    @Column(name = "group_id")
    private String groupId;
    private String username;
    private String phone;
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

    // 🔹 YENİ: statik location
    @Column(name = "location_lat", columnDefinition = "DOUBLE")
    private Double locationLat;

    @Column(name = "location_lng", columnDefinition = "DOUBLE")
    private Double locationLng;

    // 🔹 YENİ: WhatsApp jpegThumbnail (base64); uzun ola bilər, LOB kimi saxla
    @Lob
    @Column(name = "thumbnail", columnDefinition = "LONGTEXT")
    private String thumbnail;
}


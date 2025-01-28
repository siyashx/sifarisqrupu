package com.codesupreme.sifarisqrupu.model.chat;

import com.codesupreme.sifarisqrupu.dto.chat.ForwardedMessage;
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
    private String username;
    private ForwardedMessage forwardedMessage;
    private List<String> isSeenIds;
    private Boolean isForwarded;
    private String userType;
    private String message;
    private String time;
    private String date;
    private Boolean isCompleted;
}


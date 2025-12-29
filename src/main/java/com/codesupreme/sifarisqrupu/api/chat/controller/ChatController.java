package com.codesupreme.sifarisqrupu.api.chat.controller;

import com.codesupreme.sifarisqrupu.dto.chat.ChatDto;
import com.codesupreme.sifarisqrupu.service.impl.chat.ChatServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/api")
public class ChatController {

    private final ChatServiceImpl chatServiceImpl;
    private final SimpMessagingTemplate messagingTemplate;

    // WebSocket Messaging

    @Autowired
    public ChatController(ChatServiceImpl chatServiceImpl, SimpMessagingTemplate messagingTemplate) {
        this.chatServiceImpl = chatServiceImpl;
        this.messagingTemplate = messagingTemplate;
    }

    // Mesaj gönderimi WebSocket ile yapılacak
    @MessageMapping("/sendChatMessage")
    @SendTo("/topic/sifarisqrupu")
    public ChatDto sendMessage(@Payload ChatDto chatMessageDto) {
        // Mesajı kaydet ve ardından döndür
        return chatServiceImpl.createChat(chatMessageDto);
    }

    // CRUD Operations for Chat
    @GetMapping("/chats")
    public ResponseEntity<List<ChatDto>> getAllChat(
            @RequestParam(value = "groupIds", required = false) String groupIds,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit
    ) {
        List<String> gids = null;

        if (groupIds != null && !groupIds.trim().isEmpty()) {
            gids = Arrays.stream(groupIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        List<ChatDto> messages = chatServiceImpl.getLatestChats(gids, limit);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatDto> createChat(@RequestBody ChatDto chatDto) {
        ChatDto createdChat = chatServiceImpl.createChat(chatDto);
        return ResponseEntity.ok(createdChat);
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<ChatDto> findChatById(@PathVariable Long chatId) {
        ChatDto chatDto = chatServiceImpl.getChatById(chatId);
        if (chatDto != null) {
            return ResponseEntity.ok(chatDto);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/chat/{chatId}")
    public ResponseEntity<?> updateChat(
            @PathVariable Long chatId,
            @RequestBody ChatDto chatDto) {

        try {
            ChatDto updatedChat = chatServiceImpl.updateChat(chatId, chatDto);
            if (updatedChat != null) {
                return ResponseEntity.ok(updatedChat);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/chat/{chatId}")
    public ResponseEntity<String> deleteChat(@PathVariable Long chatId) {
        boolean deleted = chatServiceImpl.removeById(chatId);
        if (deleted) {
            return ResponseEntity.ok("Chat deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}


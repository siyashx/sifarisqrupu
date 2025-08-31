package com.codesupreme.sifarisqrupu.api.chat_group.controller;

import com.codesupreme.sifarisqrupu.dto.chat_group.ChatGroupDto;
import com.codesupreme.sifarisqrupu.service.impl.chat_group.ChatGroupServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/chat_group")
public class ChatGroupController {

    private final ChatGroupServiceImpl service;

    public ChatGroupController(ChatGroupServiceImpl service) {
        this.service = service;
    }

    // Bütün chat_groupləri əldə et
    @GetMapping
    public ResponseEntity<List<ChatGroupDto>> getAllChatGroups() {
        List<ChatGroupDto> all = service.getAllChatGroups();
        return ResponseEntity.ok(all);
    }

    // ID-ə görə chat_group əldə et
    @GetMapping("/{chatGroupId}")
    public ResponseEntity<ChatGroupDto> getChatGroupById(@PathVariable("chatGroupId") Long id) {
        ChatGroupDto chat_group = service.getChatGroupById(id);
        if (chat_group != null) {
            return ResponseEntity.ok(chat_group);
        }
        return ResponseEntity.notFound().build();
    }

    // Yeni chat_group əlavə et
    @PostMapping
    public ResponseEntity<ChatGroupDto> saveChatGroup(@RequestBody ChatGroupDto dto) {
        ChatGroupDto created = service.saveChatGroup(dto);
        return ResponseEntity.ok(created);
    }

    // ChatGroupı yenilə
    @PutMapping("/{chatGroupId}")
    public ResponseEntity<?> updateChatGroup(
            @PathVariable("chatGroupId") Long id,
            @RequestBody ChatGroupDto chat_groupDto) {
        ChatGroupDto updatedChatGroup = service.updateChatGroup(id, chat_groupDto);
        if (updatedChatGroup != null) {
            return ResponseEntity.ok(updatedChatGroup);
        }
        return ResponseEntity.notFound().build();
    }

    // ChatGroupı sil
    @DeleteMapping("/{chatGroupId}")
    public ResponseEntity<String> deleteChatGroup(@PathVariable("chatGroupId") Long id) {
        Boolean deleted = service.deleteChatGroup(id);
        if (deleted) {
            return ResponseEntity.ok("ChatGroup deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}

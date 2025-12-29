package com.codesupreme.sifarisqrupu.service.inter.chat;

import com.codesupreme.sifarisqrupu.dto.chat.ChatDto;

import java.util.List;

public interface ChatServiceInter {

    ChatDto createChat(ChatDto chatDto);
    List<ChatDto> getAllChats();
    List<ChatDto> getLatestChats(List<String> groupIds, int limit);
    Boolean removeById(Long chatId);
    ChatDto getChatById(Long chatId);
    ChatDto updateChat(Long chatId, ChatDto chatDto);
}

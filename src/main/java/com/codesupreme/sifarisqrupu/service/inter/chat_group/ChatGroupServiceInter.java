package com.codesupreme.sifarisqrupu.service.inter.chat_group;

import com.codesupreme.sifarisqrupu.dto.chat_group.ChatGroupDto;

import java.util.List;

public interface ChatGroupServiceInter {

    // Bütün chat_grouplari əldə et
    List<ChatGroupDto> getAllChatGroups();

    // ID-yə görə chat_group əldə et
    ChatGroupDto getChatGroupById(Long id);

    // Yeni chat_group əlavə et
    ChatGroupDto saveChatGroup(ChatGroupDto dto);

    // Mövcud chat_groupu yenilə
    ChatGroupDto updateChatGroup(Long chatGroupId, ChatGroupDto chatGroupDto);

    // ChatGroupu sil
    Boolean deleteChatGroup(Long id);
}

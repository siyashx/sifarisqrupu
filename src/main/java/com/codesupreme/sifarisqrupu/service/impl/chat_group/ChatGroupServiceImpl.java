package com.codesupreme.sifarisqrupu.service.impl.chat_group;

import com.codesupreme.sifarisqrupu.dao.chat_group.ChatGroupRepository;
import com.codesupreme.sifarisqrupu.dto.chat_group.ChatGroupDto;
import com.codesupreme.sifarisqrupu.model.chat_group.ChatGroup;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatGroupServiceImpl {

    private final ChatGroupRepository chatGroupRepository;
    private final ModelMapper modelMapper;

    public ChatGroupServiceImpl(ChatGroupRepository chatGroupRepository, ModelMapper modelMapper) {
        this.chatGroupRepository = chatGroupRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<ChatGroupDto> getAllChatGroups() {
        List<ChatGroup> list = chatGroupRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, ChatGroupDto.class))
                .toList();
    }

    //ById
    public ChatGroupDto getChatGroupById(Long id) {
        Optional<ChatGroup> optional = chatGroupRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, ChatGroupDto.class)).orElse(null);
    }

    //Save
    public ChatGroupDto saveChatGroup(ChatGroupDto dto) {
        ChatGroup chatGroup = modelMapper.map(dto, ChatGroup.class);
        chatGroup = chatGroupRepository.save(chatGroup);
        return modelMapper.map(chatGroup, ChatGroupDto.class);
    }

    //Update
    public ChatGroupDto updateChatGroup(Long chat_groupId, ChatGroupDto chatGroupDto) {
        Optional<ChatGroup> optional = chatGroupRepository.findById(chat_groupId);
        if (optional.isPresent()) {
            ChatGroup chatGroup = optional.get();

            if (chatGroupDto.getDescription() != null) {
                chatGroup.setDescription(chatGroupDto.getDescription());
            }

            if (chatGroupDto.getAdminId() != null) {
                chatGroup.setAdminId(chatGroupDto.getAdminId());
            }

            if (chatGroupDto.getOtherAdminIds() != null) {
                chatGroup.setOtherAdminIds(chatGroupDto.getOtherAdminIds());
            }

            if (chatGroupDto.getJoinedUserIds() != null) {
                chatGroup.setJoinedUserIds(chatGroupDto.getJoinedUserIds());
            }

            if (chatGroupDto.getName() != null) {
                chatGroup.setName(chatGroupDto.getName());
            }

            if (chatGroupDto.getDescription() != null) {
                chatGroup.setDescription(chatGroupDto.getDescription());
            }

            if (chatGroupDto.getMutedUserIds() != null) {
                chatGroup.setMutedUserIds(chatGroupDto.getMutedUserIds());
            }

            if (chatGroupDto.getIsPrivate() != null) {
                chatGroup.setIsPrivate(chatGroupDto.getIsPrivate());
            }

            if (chatGroupDto.getIsDisable() != null) {
                chatGroup.setIsDisable(chatGroupDto.getIsDisable());
            }

            if (chatGroupDto.getCreatedAt() != null) {
                chatGroup.setCreatedAt(chatGroupDto.getCreatedAt());
            }

            chatGroup = chatGroupRepository.save(chatGroup);

            return modelMapper.map(chatGroup, ChatGroupDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteChatGroup(Long id) {
        Optional<ChatGroup> optional = chatGroupRepository.findById(id);
        if (optional.isPresent()) {
            ChatGroup chatGroup = optional.get();
            chatGroupRepository.delete(chatGroup);
            return true;
        }
        return false;
    }
}

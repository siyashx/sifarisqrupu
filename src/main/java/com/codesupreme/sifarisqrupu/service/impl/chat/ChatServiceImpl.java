package com.codesupreme.sifarisqrupu.service.impl.chat;

import com.codesupreme.sifarisqrupu.dao.chat.ChatRepository;
import com.codesupreme.sifarisqrupu.dto.chat.ChatDto;
import com.codesupreme.sifarisqrupu.model.chat.Chat;
import com.codesupreme.sifarisqrupu.service.inter.chat.ChatServiceInter;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatServiceInter {

    private final ChatRepository chatRepository;
    private final ModelMapper modelMapper;

    @Autowired
    public ChatServiceImpl(ChatRepository chatRepository, ModelMapper modelMapper) {
        this.chatRepository = chatRepository;
        this.modelMapper = modelMapper;
    }

    private ChatDto positioned(Chat chat) {
        ChatDto dto = modelMapper.map(chat, ChatDto.class);
        if (chat.getId() == null || chat.getGroupId() == null) return dto;
        dto.setAdvertisingSequence(chatRepository.countByGroupIdInAndIdLessThan(
                List.of(chat.getGroupId()), chat.getId()) + 1);
        if ("0".equals(chat.getGroupId()) || "1".equals(chat.getGroupId())) {
            dto.setAdvertisingCombinedSequence(chatRepository.countByGroupIdInAndIdLessThan(
                    List.of("0", "1"), chat.getId()) + 1);
        }
        return dto;
    }

    @Override
    public ChatDto createChat(ChatDto chatDto) {
        Chat chat = modelMapper.map(chatDto, Chat.class);
        chat = chatRepository.save(chat);

        return positioned(chat);
    }

    @Override
    public List<ChatDto> getAllChats() {
        List<Chat> chats = chatRepository.findAll();
        return chats.stream()
                .map(chat -> modelMapper.map(chat, ChatDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ChatDto> getLatestChats(List<String> groupIds, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200)); // max 200
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Chat> chats;
        if (groupIds == null || groupIds.isEmpty()) {
            chats = chatRepository.findAllByOrderByIdDesc(pageable);
        } else {
            chats = chatRepository.findByGroupIdInOrderByIdDesc(groupIds, pageable);
        }

        // DESC gəlir, UI üçün ASC-ə çevirək (köhnədən yeniyə)
        List<Chat> asc = new ArrayList<>(chats);
        java.util.Collections.reverse(asc);

        if (asc.isEmpty()) return List.of();
        // The offset belongs to the entire selected conversation, not its latest page.
        long position = groupIds == null || groupIds.isEmpty()
                ? chatRepository.countByIdLessThan(asc.get(0).getId())
                : chatRepository.countByGroupIdInAndIdLessThan(groupIds, asc.get(0).getId());
        boolean combined = groupIds != null && groupIds.size() == 2
                && groupIds.containsAll(List.of("0", "1"));
        List<ChatDto> result = new ArrayList<>();
        for (Chat chat : asc) {
            ChatDto dto = modelMapper.map(chat, ChatDto.class);
            if (combined) dto.setAdvertisingCombinedSequence(++position);
            else dto.setAdvertisingSequence(++position);
            result.add(dto);
        }
        return result;
    }

    @Override
    public ChatDto getChatById(Long chatId) {
        Optional<Chat> chatOptional = chatRepository.findById(chatId);
        return chatOptional.map(this::positioned).orElse(null);
    }

    @Override
    public ChatDto updateChat(Long chatId, ChatDto chatDto) {
        Optional<Chat> chatOptional = chatRepository.findById(chatId);
        if (chatOptional.isPresent()) {
            Chat chat = chatOptional.get();

            if (chatDto.getGroupId() != null) {
                chat.setGroupId(chatDto.getGroupId());
            }

            // Update fields only if they are not null
            if (chatDto.getUserId() != null) {
                chat.setUserId(chatDto.getUserId());
            }

            if (chatDto.getUsername() != null) {
                chat.setUsername(chatDto.getUsername());
            }

            if (chatDto.getPhone() != null) {
                chat.setPhone(chatDto.getPhone());
            }

            if (chatDto.getIsSeenIds() != null) {
                chat.setIsSeenIds(chatDto.getIsSeenIds());
            }

            if (chatDto.getMessageType() != null) {
                chat.setMessageType(chatDto.getMessageType());
            }

            if (chatDto.getImageUrl() != null) {
                chat.setImageUrl(chatDto.getImageUrl());
            }

            if (chatDto.getAudioUrl() != null) {
                chat.setAudioUrl(chatDto.getAudioUrl());
            }

            if (chatDto.getUserType() != null) {
                chat.setUserType(chatDto.getUserType());
            }

            if (chatDto.getIsReply() != null) {
                chat.setIsReply(chatDto.getIsReply());
            }

            if (chatDto.getReplyUserId() != null) {
                chat.setReplyUserId(chatDto.getReplyUserId());
            }

            if (chatDto.getReplyMessage() != null) {
                chat.setReplyMessage(chatDto.getReplyMessage());
            }

            if (chatDto.getMessage() != null) {
                chat.setMessage(chatDto.getMessage());
            }

            if (chatDto.getIsWebsite() != null) {
                chat.setIsWebsite(chatDto.getIsWebsite());
            }

            if (chatDto.getTimestamp() != null) {
                chat.setTimestamp(chatDto.getTimestamp());
            }

            if (chatDto.getIsCompleted() != null) {
                chat.setIsCompleted(chatDto.getIsCompleted());
            }

            if (chatDto.getLocationLat() != null) {
                chat.setLocationLat(chatDto.getLocationLat());
            }

            if (chatDto.getLocationLng() != null) {
                chat.setLocationLng(chatDto.getLocationLng());
            }

            if (chatDto.getThumbnail() != null) {
                chat.setThumbnail(chatDto.getThumbnail());
            }

            chat = chatRepository.save(chat);

            return positioned(chat);
        }
        return null;
    }

    @Override
    public Boolean removeById(Long chatId) {
        Optional<Chat> chatOptional = chatRepository.findById(chatId);
        if (chatOptional.isPresent()) {
            chatRepository.delete(chatOptional.get());

            return true;
        }
        return false;
    }

}
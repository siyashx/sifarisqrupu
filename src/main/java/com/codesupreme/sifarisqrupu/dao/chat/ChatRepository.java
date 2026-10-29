package com.codesupreme.sifarisqrupu.dao.chat;

import com.codesupreme.sifarisqrupu.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @org.springframework.data.jpa.repository.Query("select new com.codesupreme.sifarisqrupu.adminchat.AdminChatMessage(c.id,c.groupId,c.userId,c.username,c.phone,c.isSeenIds,c.messageType,c.message,c.userType,c.timestamp,c.isCompleted,c.isWebsite) from Chat c where c.groupId in :groups order by c.id desc")
    List<com.codesupreme.sifarisqrupu.adminchat.AdminChatMessage> findAdminLatest(@org.springframework.data.repository.query.Param("groups") List<String> groups, Pageable pageable);

    long countByGroupIdInAndIdLessThan(List<String> groups, Long id);
    long countByIdLessThan(Long id);

    // groupId-lərə görə, ən yenidən köhnəyə (DESC) limitli
    List<Chat> findByGroupIdInOrderByIdDesc(List<String> groupIds, Pageable pageable);

    // ümumi ən yenidən köhnəyə limitli
    List<Chat> findAllByOrderByIdDesc(Pageable pageable);

}

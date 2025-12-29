package com.codesupreme.sifarisqrupu.dao.chat;

import com.codesupreme.sifarisqrupu.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    // groupId-lərə görə, ən yenidən köhnəyə (DESC) limitli
    List<Chat> findByGroupIdInOrderByIdDesc(List<String> groupIds, Pageable pageable);

    // ümumi ən yenidən köhnəyə limitli
    List<Chat> findAllByOrderByIdDesc(Pageable pageable);

}

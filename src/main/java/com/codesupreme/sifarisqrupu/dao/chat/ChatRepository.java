package com.codesupreme.sifarisqrupu.dao.chat;

import com.codesupreme.sifarisqrupu.model.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, Long> {
}

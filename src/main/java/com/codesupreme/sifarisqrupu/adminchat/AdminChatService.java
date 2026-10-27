package com.codesupreme.sifarisqrupu.adminchat;

import com.codesupreme.sifarisqrupu.dao.chat.ChatRepository;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminChatService {
    private final ChatRepository chats;
    private final AdminChatDirectory users;
    public AdminChatService(ChatRepository chats, AdminChatDirectory users) {
        this.chats = chats; this.users = users;
    }
    @Transactional(readOnly = true)
    public Map<String, Object> feed(String group) {
        List<String> ids = switch(group) {
            case "orders" -> List.of("0", "1");
            case "moto" -> List.of("1");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yanlış çat bölməsi.");
        };
        var messages = new ArrayList<>(chats.findAdminLatest(ids, PageRequest.of(0, 52)));
        Collections.reverse(messages);
        var seen = new LinkedHashSet<Long>();
        for(var message: messages) if(message.isSeenIds() != null) for(String raw: message.isSeenIds()) {
            try { long id = Long.parseLong(raw); if(id > 0) seen.add(id); } catch(RuntimeException ignored) {}
        }
        var profiles = new LinkedHashMap<String, Object>();
        var batch = new ArrayList<>(seen);
        for(int offset = 0; offset < batch.size(); offset += 200) {
            for(var user: users.profiles(batch.subList(offset, Math.min(offset + 200, batch.size())))) {
                String photo = user.photos() == null || user.photos().isEmpty() ? "" : Objects.toString(user.photos().get(0), "");
                profiles.put(user.id().toString(), Map.of("id", user.id(), "name", Objects.toString(user.name(), "İstifadəçi"), "photo", photo));
            }
        }
        return Map.of("messages", messages, "profiles", profiles);
    }
}

package com.codesupreme.sifarisqrupu.adminchat;

import com.codesupreme.sifarisqrupu.model.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminChatDirectory extends Repository<User, Long> {
    @Query("select new com.codesupreme.sifarisqrupu.adminchat.AdminChatProfile(u.id,u.name,u.identifyPhoto) from User u where u.id in :ids")
    List<AdminChatProfile> profiles(@Param("ids") Collection<Long> ids);
}

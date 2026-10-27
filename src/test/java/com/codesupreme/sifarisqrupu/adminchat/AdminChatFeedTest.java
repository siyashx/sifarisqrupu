package com.codesupreme.sifarisqrupu.adminchat;

import com.codesupreme.sifarisqrupu.dao.chat.ChatRepository;
import com.codesupreme.sifarisqrupu.model.chat.Chat;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.generate_statistics=true","spring.datasource.url=jdbc:h2:mem:adminchat;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=AdminChatFeedTest.Config.class)
class AdminChatFeedTest {
    @Configuration @EntityScan(basePackageClasses={Chat.class,User.class})
    @EnableJpaRepositories(basePackageClasses={ChatRepository.class,AdminChatDirectory.class})
    @Import(AdminChatService.class) static class Config {}
    @Autowired AdminChatService service;
    @Autowired EntityManager em;
    @Test void feedIsLimitedOrderedAndBatchLoadsOnlyMinimalProfiles() {
        var user=User.builder().name("Reader").password("private-password").identifyPhoto(List.of("https://example.com/profile.jpg","https://example.com/private-document.jpg")).build();
        em.persist(user);
        for(int i=0;i<130;i++) em.persist(Chat.builder().groupId(i%2==0?"1":"0").message("Message "+i).userId(user.getId()).username("Sender").isSeenIds(List.of(user.getId().toString(),"invalid","-1")).thumbnail("x".repeat(10000)).build());
        em.persist(Chat.builder().groupId("3").message("Other group").build());
        em.flush();em.clear();
        var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
        var result=service.feed("moto");
        var messages=(List<AdminChatMessage>)result.get("messages");
        assertEquals(52,messages.size());assertTrue(messages.stream().allMatch(m->"1".equals(m.groupId())));
        assertEquals("Message 128",messages.get(51).message());
        for(int i=1;i<messages.size();i++)assertTrue(messages.get(i).id()>messages.get(i-1).id());
        var profiles=(Map<String,Object>)result.get("profiles");
        assertEquals(Map.of("id",user.getId(),"name","Reader","photo","https://example.com/profile.jpg"),profiles.get(user.getId().toString()));
        assertEquals(2,stats.getQueryExecutionCount(),"One bounded chat query and one batched profile query");
        assertEquals(0,stats.getEntityLoadCount(),"Heavy Chat/User entities must not be loaded");
        var orders=(List<AdminChatMessage>)service.feed("orders").get("messages");assertEquals(52,orders.size());assertEquals("Message 129",orders.get(51).message());
        assertThrows(ResponseStatusException.class,()->service.feed("arbitrary-group"));
    }
    @Test void authenticationIsRequiredBeforeAnyChatRead() {
        var auth=mock(AdminPushAuth.class);var feed=mock(AdminChatService.class);
        when(auth.requireAdmin(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(ResponseStatusException.class,()->new AdminChatController(auth,feed).feed(null,"moto"));
        verifyNoInteractions(feed);
    }
}

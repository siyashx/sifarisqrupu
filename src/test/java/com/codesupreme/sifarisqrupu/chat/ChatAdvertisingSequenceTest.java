package com.codesupreme.sifarisqrupu.chat;

import com.codesupreme.sifarisqrupu.dao.chat.ChatRepository;
import com.codesupreme.sifarisqrupu.dto.chat.ChatDto;
import com.codesupreme.sifarisqrupu.model.chat.Chat;
import com.codesupreme.sifarisqrupu.service.impl.chat.ChatServiceImpl;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.generate_statistics=true","spring.datasource.url=jdbc:h2:mem:chat-ads;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ChatAdvertisingSequenceTest.Config.class)
class ChatAdvertisingSequenceTest {
    @Configuration @EntityScan(basePackageClasses=Chat.class)
    @EnableJpaRepositories(basePackageClasses=ChatRepository.class)
    @Import(ChatServiceImpl.class) static class Config {
        @Bean ModelMapper mapper() { return new ModelMapper(); }
    }
    @Autowired EntityManager em;
    @Autowired ChatServiceImpl service;

    @Test void slidingCombinedHistoryKeepsAdvertisementsOnTheSameMessages() {
        for(int i=0;i<150;i++) {
            em.persist(Chat.builder().groupId(i%2==0?"0":"1").message("Message "+i).build());
            // IDs from unrelated groups must not count as conversation messages.
            em.persist(Chat.builder().groupId("3").message("Other").build());
        }
        em.flush();em.clear();
        var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
        var before=service.getLatestChats(List.of("0","1"),15);
        assertEquals(136L,before.get(0).getAdvertisingCombinedSequence());
        assertEquals(150L,before.get(14).getAdvertisingCombinedSequence());
        assertEquals(2,stats.getQueryExecutionCount(),"One bounded page and one prefix count, never one count per message");
        var anchors=new HashMap<Long,Long>();
        before.stream().filter(m->m.getAdvertisingCombinedSequence()%5==0).forEach(m->anchors.put(m.getId(),m.getAdvertisingCombinedSequence()));
        var created=service.createChat(ChatDto.builder().groupId("1").message("New").build());
        assertEquals(151L,created.getAdvertisingCombinedSequence());
        assertEquals(76L,created.getAdvertisingSequence());
        var after=service.getLatestChats(List.of("0","1"),15);
        assertEquals(137L,after.get(0).getAdvertisingCombinedSequence());
        after.stream().filter(m->anchors.containsKey(m.getId())).forEach(m->assertEquals(anchors.get(m.getId()),m.getAdvertisingCombinedSequence()));
        var single=service.getLatestChats(List.of("1"),15);
        assertEquals(62L,single.get(0).getAdvertisingSequence());
        assertEquals(76L,single.get(14).getAdvertisingSequence());
    }

    @Test void editsAndSingleMessageReadsKeepTheServerSequenceAndIgnoreClientValues() {
        for(int i=0;i<9;i++) service.createChat(ChatDto.builder().groupId("1").message("Old").build());
        var message=service.createChat(ChatDto.builder().groupId("1").message("Tenth").advertisingSequence(999L).build());
        assertEquals(10L,message.getAdvertisingSequence());
        var edited=service.updateChat(message.getId(),ChatDto.builder().message("Edited").build());
        assertEquals(10L,edited.getAdvertisingSequence());
        assertEquals(10L,service.getChatById(message.getId()).getAdvertisingCombinedSequence());
    }
}

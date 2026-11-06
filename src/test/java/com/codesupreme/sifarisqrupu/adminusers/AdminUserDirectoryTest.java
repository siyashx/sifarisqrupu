package com.codesupreme.sifarisqrupu.adminusers;

import static org.junit.jupiter.api.Assertions.*;
import com.codesupreme.sifarisqrupu.model.user.User;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:adminusers;MODE=MySQL;NON_KEYWORDS=USER","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=AdminUserDirectoryTest.Config.class)
class AdminUserDirectoryTest {
 @TestConfiguration @EntityScan(basePackageClasses=User.class) @EnableJpaRepositories(basePackageClasses=AdminUserDirectory.class) static class Config {}
 @Autowired EntityManager em;
 @Autowired AdminUserDirectory directory;
 @Autowired NamedParameterJdbcTemplate jdbc;
 @Test void realDatabaseSearchPagingAndProjectionExcludeUnrelatedRows() {
  for(int i=0;i<87;i++) em.persist(User.builder().name(i<3?"Axtarış":"Other").phoneNumber("0551234567").password("private").userType(i%2==0?"customer":null).courierStatus(i==1?"active":"accept").identifyPhoto(List.of("https://example.com/p.jpg")).build());
  em.flush();em.clear();
  var service=new AdminUserService(jdbc,directory);
  var page=service.list("","all","none","all",0,40);
  assertEquals(40,page.items().size());assertEquals(87,page.total());assertTrue(page.hasMore());
  assertTrue(page.items().get(0).id()>page.items().get(1).id());
  var last=service.list("","all","none","all",2,40); assertEquals(7,last.items().size());assertFalse(last.hasMore());
  var pending=service.list("","all","none","active",0,40); assertEquals(1,pending.items().size());assertEquals("active",pending.items().get(0).courierStatus());
  var found=service.list("axtarış","customer","none","all",0,40);assertEquals(2,found.total());assertEquals(1,found.counts().get("courier"));
  assertEquals(List.of("https://example.com/p.jpg"),found.items().get(0).identifyPhoto());
  assertEquals(0,service.list("%","all","none","all",0,40).items().size());
 }
}

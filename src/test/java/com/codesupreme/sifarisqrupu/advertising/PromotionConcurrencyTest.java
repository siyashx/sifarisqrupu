package com.codesupreme.sifarisqrupu.advertising;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.codesupreme.sifarisqrupu.dao.elan.ElanRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.elan.Elan;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;

@DataJpaTest(properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:promotion_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=PromotionConcurrencyTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PromotionConcurrencyTest {
 @Configuration @EntityScan(basePackageClasses={AdRequest.class,Elan.class}) @EnableJpaRepositories(basePackageClasses={AdRequestRepository.class,ElanRepository.class}) @Import(AdvertisingService.class) static class Config {}
 @Autowired AdvertisingService service;@Autowired AdRequestRepository requests;@Autowired ElanRepository listings;
 @MockBean UserRepository users;@MockBean FcmPushService fcm;@MockBean SimpMessagingTemplate events;
 @Test void simultaneousPromotionRequestsShareOneRecordAndOneAdminNotification() throws Exception {
   when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).name("Seller").phoneNumber("0551234567").build()));
   var listing=listings.saveAndFlush(Elan.builder().userId(7L).title("Motorcycle").isDisable(false).build());
   var plan=service.savePlan(null,new AdvertisingService.PlanForm("SHOP","1 gün",24,new BigDecimal("5.00"),null,null,true));
   var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
   try {
     var jobs=new ArrayList<Future<Map<String,Object>>>();
     for(int i=0;i<2;i++){final int slot=i;jobs.add(pool.submit(()->{ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));return service.request(7L,new AdvertisingService.RequestForm("SHOP",null,plan.getId(),listing.getId(),null,null,null,null,null,0,"concurrent-promotion-"+slot,plan.getVersion()));}));}
     assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
     assertEquals(jobs.get(0).get(20,TimeUnit.SECONDS).get("id"),jobs.get(1).get(20,TimeUnit.SECONDS).get("id"));
     assertEquals(1,requests.count());verify(fcm,times(1)).enqueue(eq("ADMIN"),anyList(),anyString(),anyString(),anyMap(),eq(86400),anyString());
   } finally {go.countDown();pool.shutdownNow();}
 }
}

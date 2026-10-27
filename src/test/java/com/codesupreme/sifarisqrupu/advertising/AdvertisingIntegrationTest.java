package com.codesupreme.sifarisqrupu.advertising;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import com.codesupreme.sifarisqrupu.dao.elan.ElanRepository;import com.codesupreme.sifarisqrupu.dao.user.UserRepository;import com.codesupreme.sifarisqrupu.model.elan.Elan;import com.codesupreme.sifarisqrupu.model.user.User;import com.codesupreme.sifarisqrupu.push.FcmPushService;import java.util.*;import java.math.BigDecimal;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;import org.springframework.boot.autoconfigure.domain.EntityScan;import org.springframework.context.annotation.*;import org.springframework.data.jpa.repository.config.EnableJpaRepositories;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.messaging.simp.SimpMessagingTemplate;import org.springframework.test.context.ContextConfiguration;import org.springframework.web.server.ResponseStatusException;
@DataJpaTest(properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:ads;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","spring.datasource.driver-class-name=org.h2.Driver"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=AdvertisingIntegrationTest.Config.class)
class AdvertisingIntegrationTest {
 @Configuration @EntityScan(basePackageClasses=AdRequest.class) @EnableJpaRepositories(basePackageClasses=AdRequestRepository.class) @Import(AdvertisingService.class) static class Config {}
 @Autowired AdvertisingService service;@Autowired AdRequestRepository requests;@Autowired AdPlanRepository plans;@MockBean UserRepository users;@MockBean ElanRepository listings;@MockBean FcmPushService fcm;@MockBean SimpMessagingTemplate events;
 @BeforeEach void setup(){when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).name("Seller").phoneNumber("055 123-45-67").password("private").build()));when(listings.findById(8L)).thenReturn(Optional.of(Elan.builder().id(8L).title("Məhsul").userId(99L).isDisable(false).build()));}
 AdPlan plan(String kind){return service.savePlan(null,new AdvertisingService.PlanForm(kind,"1 gün",24,new BigDecimal("5.00"),null,null,true));}
 AdvertisingService.RequestForm form(AdPlan p,String key){return new AdvertisingService.RequestForm(p.getKind(),"MOTO",p.getId(),8L,"Başlıq","Açıqlama",null,"WHATSAPP",null,System.currentTimeMillis(),key,p.getVersion());}
 @Test void pendingRequestsArePrivateAndApprovalIsIdempotentWithSnapshotPrices(){var p=plan("CHAT");var r=service.request(7L,form(p,"request-key-12345678"));assertEquals("PENDING",r.get("status"));assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());assertEquals("994551234567",r.get("target"));assertEquals(r.get("id"),service.request(7L,form(p,"request-key-12345678")).get("id"));assertEquals(1,requests.count());service.savePlan(p.getId(),new AdvertisingService.PlanForm("CHAT","2 gün",48,new BigDecimal("10.00"),null,null,true));var approved=service.moderate(((Number)r.get("id")).longValue(),"approve");assertEquals(new BigDecimal("5.00"),approved.get("price"));assertEquals(24,approved.get("durationHours"));assertEquals(approved.get("endsAt"),service.moderate(((Number)r.get("id")).longValue(),"approve").get("endsAt"));var feed=(List<Map<String,Object>>)service.feed("MOTO").get("items");assertEquals(1,feed.size());assertFalse(feed.get(0).containsKey("user"));assertTrue(((List<?>)service.feed("SUSHI").get("items")).isEmpty());service.moderate(((Number)r.get("id")).longValue(),"withdraw");assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());verify(fcm,times(1)).enqueue(eq("ADMIN"),eq(List.of(1L)),anyString(),contains("1 gün"),anyMap(),eq(86400),anyString());}
 @Test void promotionCanBeRequestedForAnotherUsersListingButRequiresApproval(){var p=plan("SHOP");var r=service.request(7L,form(p,"shop-request-12345678"));assertEquals(8L,r.get("listingId"));service.moderate(((Number)r.get("id")).longValue(),"reject");assertTrue(((List<?>)service.feed("SUSHI").get("items")).isEmpty());assertThrows(ResponseStatusException.class,()->service.moderate(((Number)r.get("id")).longValue(),"approve"));}
 @Test void approvedPromotionsAppearInBothFeedsAndDisappearWhenListingDisabledOrExpired(){
  var listing=listings.findById(8L).orElseThrow();
  when(listings.findAllById(any())).thenReturn(List.of(listing));
  var p=plan("SHOP");var r=service.request(7L,form(p,"approved-shop-12345678"));
  long id=((Number)r.get("id")).longValue();
  assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());
  service.moderate(id,"approve");
  for(String channel:List.of("SUSHI","MOTO"))assertEquals(1,((List<?>)service.feed(channel).get("items")).size());
  listing.setIsDisable(true);
  assertTrue(((List<?>)service.feed("SUSHI").get("items")).isEmpty());
  listing.setIsDisable(false);
  var saved=requests.findById(id).orElseThrow();saved.setEndsAt(System.currentTimeMillis()-1);requests.saveAndFlush(saved);
  assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());
 }
 @Test void durationStartsAtApprovalEvenForAnOldPendingRequestOrAnOldClientsFutureDate(){
  var p=plan("CHAT");var f=form(p,"approval-time-12345678");
  var future=new AdvertisingService.RequestForm(f.kind(),f.channel(),f.planId(),f.listingId(),f.title(),f.description(),f.imageUrl(),f.targetType(),f.target(),System.currentTimeMillis()+86400000L,f.requestId(),f.planVersion());
  var created=service.request(7L,future);long id=((Number)created.get("id")).longValue();
  var pending=requests.findById(id).orElseThrow();pending.setCreatedAt(System.currentTimeMillis()-3*86400000L);pending.setRequestedStart(System.currentTimeMillis()+86400000L);requests.saveAndFlush(pending);
  long before=System.currentTimeMillis();var approved=service.moderate(id,"approve");long after=System.currentTimeMillis();
  long start=((Number)approved.get("startsAt")).longValue(),end=((Number)approved.get("endsAt")).longValue();
  assertTrue(start>=before&&start<=after);assertEquals(24*3600000L,end-start);
  assertEquals(approved.get("endsAt"),service.moderate(id,"approve").get("endsAt"));
 }
 @Test void pendingCountersExcludeApprovedRejectedAndOtherKinds(){
  var chat=plan("CHAT");var shop=plan("SHOP");
  var first=service.request(7L,form(chat,"pending-count-111111"));service.request(7L,form(chat,"pending-count-222222"));
  service.request(7L,form(shop,"pending-count-333333"));
  assertEquals(2,requests.countByKindAndStatus("CHAT","PENDING"));assertEquals(1,requests.countByKindAndStatus("SHOP","PENDING"));
  service.moderate(((Number)first.get("id")).longValue(),"approve");assertEquals(1,requests.countByKindAndStatus("CHAT","PENDING"));
 }
 @Test void visitsAreRealDailyUniqueUsersAcrossBothApps(){service.visit(7L);service.visit(7L);assertEquals(2L,service.visit(8L).get("count"));assertEquals(2L,service.feed("MOTO").get("visitors"));}
 @Test void linksRejectScriptsCredentialsAndInvalidPlans(){for(String v:List.of("javascript:alert(1)","http://example.com","https://u:p@example.com","file:///tmp/a"))assertFalse(AdvertisingService.validLink(v));assertTrue(AdvertisingService.validLink("https://example.com/shop?q=1"));var p=plan("CHAT");service.deletePlan(p.getId());assertThrows(ResponseStatusException.class,()->service.request(7L,form(p,"disabled-plan-12345678")));}
}

package com.codesupreme.sifarisqrupu.advertising;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import com.codesupreme.sifarisqrupu.dao.elan.ElanRepository;import com.codesupreme.sifarisqrupu.dao.user.UserRepository;import com.codesupreme.sifarisqrupu.model.elan.Elan;import com.codesupreme.sifarisqrupu.model.user.User;import com.codesupreme.sifarisqrupu.push.FcmPushService;import java.util.*;import java.math.BigDecimal;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;import org.springframework.boot.autoconfigure.domain.EntityScan;import org.springframework.context.annotation.*;import org.springframework.data.jpa.repository.config.EnableJpaRepositories;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.messaging.simp.SimpMessagingTemplate;import org.springframework.test.context.ContextConfiguration;import org.springframework.web.server.ResponseStatusException;
@DataJpaTest(properties={"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:ads;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER","spring.datasource.driver-class-name=org.h2.Driver"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=AdvertisingIntegrationTest.Config.class)
class AdvertisingIntegrationTest {
 @Configuration @EntityScan(basePackageClasses=AdRequest.class) @EnableJpaRepositories(basePackageClasses=AdRequestRepository.class) @Import(AdvertisingService.class) static class Config {}
 @Autowired SectionDailyVisitRepository sectionVisits;@Autowired AdvertisingService service;@Autowired AdRequestRepository requests;@Autowired AdPlanRepository plans;@MockBean UserRepository users;@MockBean ElanRepository listings;@MockBean FcmPushService fcm;@MockBean SimpMessagingTemplate events;
 @BeforeEach void setup(){when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).name("Seller").phoneNumber("055 123-45-67").password("private").build()));when(listings.findById(8L)).thenReturn(Optional.of(Elan.builder().id(8L).title("Məhsul").userId(99L).isDisable(false).build()));when(listings.lockForPromotion(8L)).thenAnswer(inv->listings.findById(8L));}
 AdPlan plan(String kind){return service.savePlan(null,new AdvertisingService.PlanForm(kind,"1 gün",24,new BigDecimal("5.00"),null,null,true));}
 AdvertisingService.RequestForm form(AdPlan p,String key){return new AdvertisingService.RequestForm(p.getKind(),"MOTO",p.getId(),8L,"Başlıq","Açıqlama",null,"WHATSAPP",null,System.currentTimeMillis(),key,p.getVersion());}
 @Test void pendingRequestsArePrivateAndApprovalIsIdempotentWithSnapshotPrices(){var p=plan("CHAT");var r=service.request(7L,form(p,"request-key-12345678"));assertEquals("PENDING",r.get("status"));assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());assertEquals("994551234567",r.get("target"));assertEquals(r.get("id"),service.request(7L,form(p,"request-key-12345678")).get("id"));assertEquals(1,requests.count());service.savePlan(p.getId(),new AdvertisingService.PlanForm("CHAT","2 gün",48,new BigDecimal("10.00"),null,null,true));var approved=service.moderate(((Number)r.get("id")).longValue(),"approve");assertEquals(new BigDecimal("5.00"),approved.get("price"));assertEquals(24,approved.get("durationHours"));assertEquals(approved.get("endsAt"),service.moderate(((Number)r.get("id")).longValue(),"approve").get("endsAt"));var feed=(List<Map<String,Object>>)service.feed("MOTO").get("items");assertEquals(1,feed.size());assertFalse(feed.get(0).containsKey("user"));assertEquals(feed,service.feed("SUSHI").get("items"));service.moderate(((Number)r.get("id")).longValue(),"withdraw");assertTrue(((List<?>)service.feed("MOTO").get("items")).isEmpty());verify(fcm,times(1)).enqueue(eq("ADMIN"),eq(List.of(1L)),anyString(),contains("1 gün"),anyMap(),eq(86400),anyString());}
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
 @Test void ownChatAdsIncludeBothSectionsAndHistoryButNeverAnotherUsersRequests(){
  var plan=plan("CHAT");
  long first=((Number)service.request(7L,form(plan,"own-ad-request-111111")).get("id")).longValue();
  service.request(99L,new AdvertisingService.RequestForm("CHAT","MOTO",plan.getId(),null,"Private other ad","",null,"LINK","https://example.com",0,"other-user-request-11111",null));
  service.request(7L,new AdvertisingService.RequestForm("CHAT","SUSHI",plan.getId(),null,"Sushi ad","",null,"WHATSAPP",null,0,"other-channel-1111111",null));
  var rows=(List<Map<String,Object>>)service.mine(7L,"MOTO").get("items");
  assertEquals(2,rows.size());assertTrue(rows.stream().anyMatch(row->Objects.equals(row.get("id"),first)));assertTrue(rows.stream().allMatch(row->"PENDING".equals(row.get("displayStatus"))&&!row.containsKey("user")));assertEquals(rows,service.mine(7L,"SUSHI").get("items"));
  service.moderate(first,"approve");
  assertEquals("APPROVED",((List<Map<String,Object>>)service.mine(7L,"MOTO").get("items")).stream().filter(row->Objects.equals(row.get("id"),first)).findFirst().orElseThrow().get("displayStatus"));
  var saved=requests.findById(first).orElseThrow();saved.setEndsAt(System.currentTimeMillis()-1000);requests.saveAndFlush(saved);
  assertEquals("EXPIRED",((List<Map<String,Object>>)service.mine(7L,"MOTO").get("items")).stream().filter(row->Objects.equals(row.get("id"),first)).findFirst().orElseThrow().get("displayStatus"));
  assertTrue(((List<?>)service.feed("MOTO").get("items")).stream().map(v->(Map<?,?>)v).noneMatch(v->Objects.equals(v.get("id"),first)));
  assertThrows(ResponseStatusException.class,()->service.mine(7L,"INVALID"));
 }
 @Test void approvedChatAdsFromEitherSectionAppearEverywhereAndInactiveAdsDisappear(){
  var plan=plan("CHAT");
  long moto=((Number)service.request(7L,form(plan,"shared-moto-12345678")).get("id")).longValue();
  long sushi=((Number)service.request(7L,new AdvertisingService.RequestForm("CHAT","SUSHI",plan.getId(),null,"Sushi ad","",null,"WHATSAPP",null,0,"shared-sushi-12345678",plan.getVersion())).get("id")).longValue();
  for(String channel:List.of("MOTO","SUSHI"))assertTrue(((List<?>)service.feed(channel).get("items")).isEmpty());
  service.moderate(moto,"approve");service.moderate(sushi,"approve");
  var older=requests.findById(moto).orElseThrow();older.setCreatedAt(System.currentTimeMillis()-5000);requests.saveAndFlush(older);
  for(String channel:List.of("MOTO","SUSHI")){
   var rows=(List<Map<String,Object>>)service.feed(channel).get("items");
   assertEquals(List.of(sushi,moto),rows.stream().map(row->row.get("id")).toList());
  }
  service.moderate(moto,"withdraw");
  for(String channel:List.of("MOTO","SUSHI"))assertEquals(1,((List<?>)service.feed(channel).get("items")).size());
  var expired=requests.findById(sushi).orElseThrow();expired.setEndsAt(System.currentTimeMillis()-1);requests.saveAndFlush(expired);
  for(String channel:List.of("MOTO","SUSHI"))assertTrue(((List<?>)service.feed(channel).get("items")).isEmpty());
 }
 AdvertisingService.EditRequest editForm(Map<String,Object> ad){return new AdvertisingService.EditRequest("Yeni başlıq","Yeni mətn","https://example.com/new.png",48,new BigDecimal("7.50"),"SUSHI","LINK","https://example.com",((Number)ad.get("version")).longValue());}
 @Test void adminEditsChatAndPromotionsWithoutRestartingPublicationAndDeletesFromEveryFeed(){
  var chat=plan("CHAT");var created=service.request(7L,form(chat,"edit-chat-123456789"));long id=((Number)created.get("id")).longValue();
  var approved=service.moderate(id,"approve");long started=((Number)approved.get("startsAt")).longValue();
  var edited=service.editRequest(id,editForm(approved));
  assertEquals("Yeni başlıq",edited.get("title"));assertEquals("SUSHI",edited.get("channel"));assertEquals(started,edited.get("startsAt"));assertEquals(started+48*3600000L,edited.get("endsAt"));assertEquals("APPROVED",edited.get("status"));
  assertThrows(ResponseStatusException.class,()->service.editRequest(id,editForm(approved)));
  service.deleteRequest(id);service.deleteRequest(id);
  assertEquals("DELETED",requests.findById(id).orElseThrow().getStatus());assertTrue(service.all().isEmpty());assertTrue(((List<?>)service.mine(7L,"SUSHI").get("items")).isEmpty());assertTrue(((List<?>)service.feed("SUSHI").get("items")).isEmpty());
  assertThrows(ResponseStatusException.class,()->service.moderate(id,"approve"));assertThrows(ResponseStatusException.class,()->service.editRequest(id,editForm(edited)));
  var shop=service.request(7L,form(plan("SHOP"),"edit-shop-123456789"));long shopId=((Number)shop.get("id")).longValue();
  var updated=service.editRequest(shopId,editForm(shop));assertEquals("SHOP",updated.get("kind"));assertEquals(8L,updated.get("listingId"));assertNull(updated.get("startsAt"));assertEquals("PENDING",updated.get("status"));
  assertEquals("Məhsul",listings.findById(8L).orElseThrow().getTitle());service.deleteRequest(shopId);assertTrue(service.all().isEmpty());assertEquals(0,requests.countByKindAndStatus("SHOP","PENDING"));
 }
 @Test void invalidAdminAdEditsAreRejected(){
  var ad=service.request(7L,form(plan("CHAT"),"invalid-edit-1234567"));long id=((Number)ad.get("id")).longValue(),version=((Number)ad.get("version")).longValue();
  for(var f:List.of(new AdvertisingService.EditRequest("","",null,24,BigDecimal.ONE,"MOTO","LINK","https://example.com",version),new AdvertisingService.EditRequest("Title","","http://example.com/a.jpg",24,BigDecimal.ONE,"MOTO","LINK","https://example.com",version),new AdvertisingService.EditRequest("Title","",null,1,BigDecimal.ONE,"MOTO","LINK","https://example.com",version),new AdvertisingService.EditRequest("Title","",null,24,BigDecimal.ONE,"MOTO","LINK","javascript:alert(1)",version)))assertThrows(ResponseStatusException.class,()->service.editRequest(id,f));
 }
 @Test void adminCreatesLiveChatWithTheChosenOwnerImageAndPlanWithoutARequestNotification(){
  var p=plan("CHAT");var f=new AdvertisingService.AdminChatForm(7L,p.getId(),p.getVersion(),"Admin reklamı","Mətn","https://example.com/photo.png","MOTO","WHATSAPP","0551234567","admin-created-123456789");
  long before=System.currentTimeMillis();var ad=service.createAdminChat(f);long id=((Number)ad.get("id")).longValue();assertEquals("APPROVED",ad.get("status"));assertEquals("https://example.com/photo.png",ad.get("imageUrl"));assertEquals("994551234567",ad.get("target"));assertTrue(((Number)ad.get("startsAt")).longValue()>=before);assertEquals(24*3600000L,((Number)ad.get("endsAt")).longValue()-((Number)ad.get("startsAt")).longValue());assertEquals(id,service.createAdminChat(f).get("id"));assertEquals(1,requests.count());verifyNoInteractions(fcm);
  assertEquals(1,((List<?>)service.mine(7L,"MOTO").get("items")).size());assertEquals(1,((List<?>)service.feed("MOTO").get("items")).size());
 }
 @Test void expiredCancelledAndRejectedAdsRestartFromNowWithANewMatchingPlan(){
  for(String kind:List.of("CHAT","SHOP")){
   var p=plan(kind);var created=service.request(7L,form(p,"renew-original-"+kind+"123456"));long id=((Number)created.get("id")).longValue();
   var active=service.moderate(id,"approve");
   assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(p.getId(),p.getVersion(),((Number)active.get("version")).longValue())));
   var saved=requests.findById(id).orElseThrow();saved.setStartsAt(System.currentTimeMillis()-86400000L);saved.setEndsAt(System.currentTimeMillis()-1);requests.saveAndFlush(saved);
   var expired=service.view(saved);assertEquals("EXPIRED",expired.get("displayStatus"));assertThrows(ResponseStatusException.class,()->service.moderate(id,"withdraw"));
   var renewedPlan=service.savePlan(null,new AdvertisingService.PlanForm(kind,"3 gün",72,new BigDecimal("12.50"),null,null,true));long before=System.currentTimeMillis();
   var renewed=service.reactivate(id,new AdvertisingService.ReactivateForm(renewedPlan.getId(),renewedPlan.getVersion(),((Number)expired.get("version")).longValue()));assertEquals("APPROVED",renewed.get("displayStatus"));assertTrue(((Number)renewed.get("startsAt")).longValue()>=before);assertEquals(72*3600000L,((Number)renewed.get("endsAt")).longValue()-((Number)renewed.get("startsAt")).longValue());assertEquals(new BigDecimal("12.50"),renewed.get("price"));
   assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(renewedPlan.getId(),renewedPlan.getVersion(),((Number)expired.get("version")).longValue())));
   var cancelled=service.moderate(id,"withdraw");service.reactivate(id,new AdvertisingService.ReactivateForm(p.getId(),p.getVersion(),((Number)cancelled.get("version")).longValue()));
   service.deleteRequest(id);assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(p.getId(),p.getVersion(),requests.findById(id).orElseThrow().getVersion())));
  }
 }
 @Test void reactivationRejectsDisabledOrWrongKindPlansAndDeletedShopListings(){
  var p=plan("CHAT");var a=service.request(7L,form(p,"renew-validation-123456"));long id=((Number)a.get("id")).longValue();var rejected=service.moderate(id,"reject");var shop=plan("SHOP");
  assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(shop.getId(),shop.getVersion(),((Number)rejected.get("version")).longValue())));
  service.deletePlan(p.getId());assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(p.getId(),p.getVersion(),((Number)rejected.get("version")).longValue())));
 }
 @Test void customerCanUseAnotherWhatsAppNumberWithoutChangingTheirProfile(){
   var p=plan("CHAT");var f=form(p,"custom-whatsapp-1234567");
   var ad=service.request(7L,new AdvertisingService.RequestForm(f.kind(),f.channel(),f.planId(),f.listingId(),f.title(),f.description(),f.imageUrl(),f.targetType(),f.target(),f.requestedStart(),f.requestId(),f.planVersion(),"+994 70 123 45 67"));
   assertEquals("994701234567",ad.get("target"));assertEquals("055 123-45-67",users.findById(7L).orElseThrow().getPhoneNumber());
   assertThrows(ResponseStatusException.class,()->service.request(7L,new AdvertisingService.RequestForm(f.kind(),f.channel(),f.planId(),f.listingId(),f.title(),f.description(),f.imageUrl(),f.targetType(),f.target(),f.requestedStart(),"invalid-whatsapp-12345",f.planVersion(),"123")));
 }
 @Test void sameListingHasOnlyOneOpenRequestPerUserAndCanBePromotedAgainAfterExpiry(){
   var p=plan("SHOP");var first=service.request(7L,form(p,"first-promotion-123456"));
   assertEquals(first.get("id"),service.request(7L,form(p,"second-promotion-12345")).get("id"));assertEquals(1,requests.count());
   assertEquals(first.get("id"),((Map<?,?>)service.promotion(7L,8L).get("request")).get("id"));assertNull(service.promotion(99L,8L).get("request"));
   long id=((Number)first.get("id")).longValue();service.moderate(id,"approve");assertEquals(id,service.request(7L,form(p,"active-promotion-12345")).get("id"));
   var old=requests.findById(id).orElseThrow();old.setEndsAt(System.currentTimeMillis()-1);requests.saveAndFlush(old);assertNull(service.promotion(7L,8L).get("request"));
   var next=service.request(7L,form(p,"expired-promotion-1234"));assertNotEquals(id,next.get("id"));assertEquals(2,requests.count());
   assertThrows(ResponseStatusException.class,()->service.reactivate(id,new AdvertisingService.ReactivateForm(p.getId(),p.getVersion(),old.getVersion())));
   service.moderate(((Number)next.get("id")).longValue(),"reject");assertNull(service.promotion(7L,8L).get("request"));
   assertNotEquals(next.get("id"),service.request(7L,form(p,"rejected-promotion-1234")).get("id"));
   verify(fcm,times(3)).enqueue(eq("ADMIN"),anyList(),anyString(),anyString(),anyMap(),eq(86400),anyString());
 }
 @Test void entriesCountEveryVisitAndShareEachSectionAcrossBothApps(){
  assertEquals(1L,service.visit(7L,"SHOP").get("count"));
  assertEquals(2L,service.visit(7L,"SHOP").get("count"));
  assertEquals(3L,service.visit(8L).get("count"));
  assertEquals(1L,service.visit(7L,"SUSHI").get("count"));
  assertEquals(2L,service.visit(7L,"SUSHI").get("count"));
  assertEquals(1L,service.visit(7L,"MOTO").get("count"));
  for(String channel:List.of("SUSHI","MOTO")){
   var feed=service.feed(channel);
   assertEquals(3L,feed.get("visitors"));
   assertEquals(Map.of("SHOP",3L,"SUSHI",2L,"MOTO",1L),feed.get("sectionVisits"));
  }
  assertThrows(ResponseStatusException.class,()->service.visit(7L,"INVALID"));
 }
 @Test void sectionEntriesResetOnBakuDayAndDoNotReuseOldUniqueVisitorRows(){
  var yesterday=new SectionDailyVisit();yesterday.setId("2020-01-01-SHOP");yesterday.setVisitDay("2020-01-01");yesterday.setSection("SHOP");yesterday.setEntryCount(77);
  sectionVisits.saveAndFlush(yesterday);
  assertEquals(1L,service.visit(7L,"SHOP").get("count"));
  assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Baku")).toString(),service.visit(7L,"MOTO").get("day"));
 }

 @Test void linksRejectScriptsCredentialsAndInvalidPlans(){for(String v:List.of("javascript:alert(1)","http://example.com","https://u:p@example.com","file:///tmp/a"))assertFalse(AdvertisingService.validLink(v));assertTrue(AdvertisingService.validLink("https://example.com/shop?q=1"));var p=plan("CHAT");service.deletePlan(p.getId());assertThrows(ResponseStatusException.class,()->service.request(7L,form(p,"disabled-plan-12345678")));}
}

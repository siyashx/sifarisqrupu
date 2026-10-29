package com.codesupreme.sifarisqrupu.advertising;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dao.elan.ElanRepository;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import java.util.*;import java.net.URI;import java.time.*;import java.math.BigDecimal;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.simp.SimpMessagingTemplate;import org.springframework.transaction.support.*;
@Service public class AdvertisingService {
 private record CachedFeed(long loadedAt,String day,Map<String,Object> value){}
 private final java.util.concurrent.ConcurrentHashMap<String,CachedFeed> feedCache=new java.util.concurrent.ConcurrentHashMap<>();
 private final List<Long> adminRecipients;
 private final AdPlanRepository plans;private final AdRequestRepository requests;private final SectionDailyVisitRepository visits;
 private final UserRepository users;private final ElanRepository listings;private final FcmPushService fcm;private final SimpMessagingTemplate events;
 public AdvertisingService(AdPlanRepository p,AdRequestRepository r,SectionDailyVisitRepository v,UserRepository u,ElanRepository e,FcmPushService f,SimpMessagingTemplate s,@org.springframework.beans.factory.annotation.Value("${push.admin.recipient-ids:1}") String recipientIds){adminRecipients=Arrays.stream(recipientIds.split(",")).map(String::trim).filter(value->value.matches("[1-9][0-9]*")).map(Long::parseLong).distinct().toList();plans=p;requests=r;visits=v;users=u;listings=e;fcm=f;events=s;}
 public record PlanForm(String kind,String label,int durationHours,BigDecimal price,Long availableFrom,Long availableUntil,boolean enabled){}
 public record RequestForm(String kind,String channel,Long planId,Long listingId,String title,String description,String imageUrl,String targetType,String target,long requestedStart,String requestId,Long planVersion,String whatsappNumber){
   public RequestForm(String kind,String channel,Long planId,Long listingId,String title,String description,String imageUrl,String targetType,String target,long requestedStart,String requestId,Long planVersion){this(kind,channel,planId,listingId,title,description,imageUrl,targetType,target,requestedStart,requestId,planVersion,null);}
 }
 private static void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 private static String clean(String v){return v==null?"":v.trim();}
 public static boolean validLink(String value){try{var u=URI.create(value);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getPort()==-1&&value.length()<=2000;}catch(Exception e){return false;}}
 private static String phone(String value){String n=clean(value).replaceAll("\\D","");if(n.startsWith("0"))n="994"+n.substring(1);require(n.matches("994[0-9]{9}"),"WhatsApp nömrəsini profilinizdə düzgün qeyd edin.");return n;}
 private void changed(){feedCache.clear();Runnable r=()->{feedCache.clear();events.convertAndSend("/topic/advertising",Map.of("changed",true));};if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){r.run();}});else r.run();}
 @Transactional public AdPlan savePlan(Long id,PlanForm f){require(Set.of("SHOP","CHAT").contains(f.kind())&&!clean(f.label()).isEmpty()&&f.label().length()<=100,"Müddət başlığını və reklam növünü seçin.");require(f.durationHours()>0&&f.durationHours()<=8760&&f.price()!=null&&f.price().signum()>=0&&f.price().scale()<=2&&f.price().compareTo(new BigDecimal("99999999"))<=0,"Müddət və qiyməti düzgün yazın.");require(f.availableFrom()==null||f.availableUntil()==null||f.availableUntil()>f.availableFrom(),"Tarix aralığını yoxlayın.");var p=id==null?new AdPlan():plans.findById(id).orElseThrow();p.setKind(f.kind());p.setLabel(clean(f.label()));p.setDurationHours(f.durationHours());p.setPrice(f.price());p.setAvailableFrom(f.availableFrom());p.setAvailableUntil(f.availableUntil());p.setEnabled(f.enabled());plans.saveAndFlush(p);changed();return p;}
 @Transactional public void deletePlan(Long id){var p=plans.findById(id).orElseThrow();p.setEnabled(false);plans.save(p);changed();}
 @Transactional(readOnly=true) public List<AdPlan> plans(boolean admin){long now=System.currentTimeMillis();return plans.findAll().stream().filter(p->admin||p.isEnabled()&&(p.getAvailableFrom()==null||p.getAvailableFrom()<=now)&&(p.getAvailableUntil()==null||p.getAvailableUntil()>now)).sorted(Comparator.comparing(AdPlan::getKind).thenComparingInt(AdPlan::getDurationHours)).toList();}
 @Transactional public Map<String,Object> request(Long user,RequestForm f){
 require(f!=null&&f.requestId()!=null&&f.requestId().matches("[a-zA-Z0-9-]{16,64}"),"Sorğunu yoxlayın.");
 // Lock the listing before any consistent read: MariaDB REPEATABLE READ must
 // create its snapshot after the previous promotion transaction has committed.
 if("SHOP".equals(f.kind())){require(f.listingId()!=null,"Elan seçin.");listings.lockForPromotion(f.listingId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Elan tapılmadı."));}
 var old=requests.findByUserIdAndRequestKey(user,f.requestId());if(old.isPresent())return view(old.get());
 if("SHOP".equals(f.kind())){var open=requests.openPromotions(user,f.listingId(),System.currentTimeMillis());if(!open.isEmpty())return view(open.get(0));}
 var plan=plans(false).stream().filter(p->Objects.equals(p.getId(),f.planId())).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Qiymət dəyişib. Yenidən müddət seçin."));require(f.planVersion()==null||f.planVersion()==plan.getVersion(),"Qiymət yenilənib. Yenidən müddət seçin.");require(Objects.equals(plan.getKind(),f.kind()),"Reklam növü uyğun deyil.");long now=System.currentTimeMillis();var a=new AdRequest();a.setUserId(user);a.setRequestKey(f.requestId());a.setKind(f.kind());a.setPlanId(plan.getId());a.setPlanLabel(plan.getLabel());a.setPrice(plan.getPrice());a.setDurationHours(plan.getDurationHours());a.setRequestedStart(now);a.setCreatedAt(now);
 if("SHOP".equals(f.kind())){require(f.listingId()!=null,"Elan seçin.");var e=listings.findById(f.listingId()).orElseThrow();require(!Boolean.TRUE.equals(e.getIsDisable()),"Elan aktiv deyil.");a.setListingId(e.getId());a.setTitle(clean(e.getTitle()));a.setDescription(clean(e.getDescription()));a.setImageUrl(e.getImages()==null||e.getImages().isEmpty()?null:e.getImages().get(0));}
 else {require(("SUSHI".equals(f.channel())||"MOTO".equals(f.channel())),"Çat bölməsini seçin.");require(!clean(f.title()).isEmpty()&&clean(f.title()).length()<=140&&clean(f.description()).length()<=4000,"Başlıq və açıqlamanı yoxlayın.");require(clean(f.imageUrl()).isEmpty()||validLink(f.imageUrl()),"Şəkil HTTPS keçidi olmalıdır.");a.setChannel(f.channel());a.setTitle(clean(f.title()));a.setDescription(clean(f.description()));a.setImageUrl(clean(f.imageUrl()));a.setTargetType(f.targetType());if("WHATSAPP".equals(f.targetType()))a.setTarget(phone(clean(f.whatsappNumber()).isEmpty()?users.findById(user).orElseThrow().getPhoneNumber():f.whatsappNumber()));else{require("LINK".equals(f.targetType())&&validLink(f.target()),"Keçidi https://example.com formatında yazın.");a.setTarget(f.target());}}
 requests.saveAndFlush(a);fcm.enqueue("ADMIN",adminRecipients,"Yeni "+("CHAT".equals(a.getKind())?"çat reklamı":"elanı irəli çəkmə")+" sorğusu",a.getTitle()+" — "+a.getPlanLabel()+" ("+a.getDurationHours()+" saat) · "+a.getPrice()+" AZN",Map.of("scope","advertising","event","request","channel","admin_advertising","adId",a.getId(),"kind",a.getKind()),86400,"ad-request-"+a.getId());changed();return view(a);}
 public Map<String,Object> view(AdRequest a){return view(a,true);}
 private Map<String,Object> view(AdRequest a,boolean contact){var u=contact?users.findById(a.getUserId()).orElse(null):null;var r=new LinkedHashMap<String,Object>();r.put("id",a.getId());r.put("version",a.getVersion());r.put("kind",a.getKind());r.put("channel",a.getChannel());r.put("listingId",a.getListingId());r.put("title",a.getTitle());r.put("description",a.getDescription());r.put("imageUrl",a.getImageUrl());r.put("targetType",a.getTargetType());r.put("target",a.getTarget());r.put("status",a.getStatus());r.put("displayStatus","APPROVED".equals(a.getStatus())&&a.getEndsAt()!=null&&a.getEndsAt()<=System.currentTimeMillis()?"EXPIRED":a.getStatus());r.put("planLabel",a.getPlanLabel());r.put("price",a.getPrice());r.put("durationHours",a.getDurationHours());r.put("createdAt",a.getCreatedAt());r.put("requestedStart",a.getRequestedStart());r.put("startsAt",a.getStartsAt());r.put("endsAt",a.getEndsAt());if(contact)r.put("user",Map.of("id",a.getUserId(),"name",u==null?"İstifadəçi":Objects.toString(u.getName(),""),"phoneNumber",u==null?"":Objects.toString(u.getPhoneNumber(),"")));return r;}
 @Transactional(readOnly=true) public List<Map<String,Object>> all(){return requests.findAllByOrderByCreatedAtDesc().stream().filter(a->!"DELETED".equals(a.getStatus())).map(this::view).toList();}
 @Transactional(readOnly=true) public Map<String,Object> mine(Long user,String channel){
   require(Set.of("SUSHI","MOTO").contains(channel),"Çat bölməsini yoxlayın.");
   long now=System.currentTimeMillis();
   var rows=requests.findTop50ByUserIdAndKindAndStatusNotOrderByCreatedAtDescIdDesc(user,"CHAT","DELETED").stream().map(a->{
     var result=view(a,false);
     result.put("displayStatus","APPROVED".equals(a.getStatus()) && a.getEndsAt()!=null && a.getEndsAt()<=now?"EXPIRED":a.getStatus());
     return result;
   }).toList();
   return Map.of("items",rows,"serverNow",now);
 }
 @Transactional(readOnly=true) public Map<String,Object> promotion(Long user,Long listing){
   var rows=requests.openPromotions(user,listing,System.currentTimeMillis());
   var result=new LinkedHashMap<String,Object>();result.put("request",rows.isEmpty()?null:view(rows.get(0),false));return result;
 }
 @Transactional(readOnly=true) public Map<String,Object> feed(String channel){
   require("SUSHI".equals(channel)||"MOTO".equals(channel),"Çat bölməsini yoxlayın.");
   long now=System.currentTimeMillis();String day=LocalDate.now(ZoneId.of("Asia/Baku")).toString();
   if(!TransactionSynchronizationManager.isCurrentTransactionReadOnly())return loadFeed(channel,now,day);
   return feedCache.compute(channel,(key,old)->old!=null&&now-old.loadedAt()<3000&&day.equals(old.day())?old:new CachedFeed(now,day,loadFeed(channel,now,day))).value();
 }
 private Map<String,Object> loadFeed(String channel,long now,String day){
   var active=requests.findByStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByCreatedAtDesc("APPROVED",now,now);
   var listingIds=active.stream().filter(a->"SHOP".equals(a.getKind())).map(AdRequest::getListingId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
   var validListings=listings.findAllById(listingIds).stream().filter(e->!Boolean.TRUE.equals(e.getIsDisable())).map(e->e.getId()).collect(java.util.stream.Collectors.toSet());
   // CHAT advertising is shared across both applications and chat sections.
   var rows=active.stream()
     .filter(a->!"SHOP".equals(a.getKind())||validListings.contains(a.getListingId())).map(a->view(a,false)).toList();
   var counts = new LinkedHashMap<String,Long>();
   for (String section : List.of("SHOP","SUSHI","MOTO")) counts.put(section,0L);
   for (var entry : visits.findByVisitDay(day)) counts.put(entry.getSection(),entry.getEntryCount());
   return Map.of("items",rows,"plans",plans(false),"visitors",counts.get("SHOP"),"sectionVisits",counts,"visitDay",day,"serverNow",now);
 }
 @Transactional public Map<String,Object> visit(Long user){return visit(user,"SHOP");}
 @Transactional public Map<String,Object> visit(Long user,String section){
   require(Set.of("SHOP","SUSHI","MOTO").contains(section),"Bölməni yoxlayın.");
   String day=LocalDate.now(ZoneId.of("Asia/Baku")).toString(),id=day+"-"+section;
   visits.increment(id,day,section);
   long count=visits.entryCount(id);
   feedCache.clear();
   if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){feedCache.clear();}});
   return Map.of("section",section,"day",day,"count",count,"serverNow",System.currentTimeMillis());
 }

 public record AdminChatForm(Long userId,Long planId,Long planVersion,String title,String description,String imageUrl,String channel,String targetType,String target,String requestId){}
 public record ReactivateForm(Long planId,Long planVersion,Long version){}
 private AdPlan selectedPlan(Long id,Long version,String kind){
   var plan=plans(false).stream().filter(p->Objects.equals(p.getId(),id)&&kind.equals(p.getKind())).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Paket aktiv deyil. Yenidən seçin."));
   require(version!=null&&version==plan.getVersion(),"Paket dəyişib. Yenidən seçin.");return plan;
 }
 private void snapshotPlan(AdRequest a,AdPlan p){a.setPlanId(p.getId());a.setPlanLabel(p.getLabel());a.setPrice(p.getPrice());a.setDurationHours(p.getDurationHours());}
 @Transactional public Map<String,Object> createAdminChat(AdminChatForm form){
   require(form.userId()!=null&&form.requestId()!=null&&form.requestId().matches("[a-zA-Z0-9-]{16,64}"),"İstifadəçi seçin.");
   var owner=users.findById(form.userId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"İstifadəçi tapılmadı."));
   require(!Boolean.TRUE.equals(owner.getIsDisable()),"İstifadəçi deaktivdir.");
   var existing=requests.findByUserIdAndRequestKey(form.userId(),form.requestId());
   if(existing.isPresent()){require("CHAT".equals(existing.get().getKind())&&!"DELETED".equals(existing.get().getStatus()),"Sorğunu yeniləyin.");return view(existing.get());}
   var plan=selectedPlan(form.planId(),form.planVersion(),"CHAT");
   require(!clean(form.title()).isEmpty()&&clean(form.title()).length()<=140&&clean(form.description()).length()<=4000,"Başlıq və açıqlamanı yoxlayın.");
   require(clean(form.imageUrl()).isEmpty()||validLink(form.imageUrl()),"Şəkli yenidən seçin.");
   require(Set.of("MOTO","SUSHI").contains(Objects.toString(form.channel(),"")),"Çat bölməsini seçin.");
   var a=new AdRequest();a.setUserId(form.userId());a.setRequestKey(form.requestId());a.setKind("CHAT");a.setChannel(form.channel());a.setTitle(clean(form.title()));a.setDescription(clean(form.description()));a.setImageUrl(clean(form.imageUrl()));
   a.setTargetType(form.targetType());
   if("WHATSAPP".equals(form.targetType()))a.setTarget(phone(form.target()));else{require("LINK".equals(form.targetType())&&validLink(form.target()),"HTTPS keçidini yoxlayın.");a.setTarget(form.target());}
   snapshotPlan(a,plan);long now=System.currentTimeMillis();a.setCreatedAt(now);a.setRequestedStart(now);a.setStartsAt(now);a.setEndsAt(now+a.getDurationHours()*3600000L);a.setStatus("APPROVED");requests.saveAndFlush(a);changed();return view(a);
 }
 @Transactional public Map<String,Object> reactivate(Long id,ReactivateForm form){
   var a=requests.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Reklam tapılmadı."));
   require(form.version()!=null&&form.version()==a.getVersion(),"Reklam dəyişib. Səhifəni yeniləyin.");
   require(Set.of("REJECTED","CANCELLED").contains(a.getStatus())||("APPROVED".equals(a.getStatus())&&a.getEndsAt()!=null&&a.getEndsAt()<=System.currentTimeMillis()),"Yalnız bitmiş və ya dayandırılmış reklam yenidən aktivləşdirilə bilər.");
   if("SHOP".equals(a.getKind())){
     require(listings.lockForPromotion(a.getListingId()).map(e->!Boolean.TRUE.equals(e.getIsDisable())).orElse(false),"Elan aktiv deyil.");
     require(requests.openPromotions(a.getUserId(),a.getListingId(),System.currentTimeMillis()).stream().noneMatch(other->!other.getId().equals(id)),"Bu elan üçün artıq aktiv reklam sorğusu var.");
   }
   var plan=selectedPlan(form.planId(),form.planVersion(),a.getKind());
   snapshotPlan(a,plan);long now=System.currentTimeMillis();a.setStartsAt(now);a.setEndsAt(now+a.getDurationHours()*3600000L);a.setStatus("APPROVED");requests.saveAndFlush(a);changed();return view(a);
 }
 public record EditRequest(String title,String description,String imageUrl,int durationHours,BigDecimal price,String channel,String targetType,String target,Long version){}
 @Transactional public Map<String,Object> editRequest(Long id,EditRequest form){
   var a=requests.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Reklam tapılmadı."));
   require(!"DELETED".equals(a.getStatus()),"Reklam silinib.");
   require(form.version()!=null && form.version()==a.getVersion(),"Reklam dəyişib. Səhifəni yeniləyib yenidən düzəldin.");
   require(!clean(form.title()).isEmpty()&&clean(form.title()).length()<=140&&clean(form.description()).length()<=4000,"Başlıq və açıqlamanı yoxlayın.");
   require(clean(form.imageUrl()).isEmpty()||validLink(form.imageUrl()),"Şəkil HTTPS keçidi olmalıdır.");
   require(form.durationHours()>=24&&form.durationHours()<=8760&&form.durationHours()%24==0&&form.price()!=null&&form.price().signum()>=0&&form.price().scale()<=2&&form.price().compareTo(new BigDecimal("99999999"))<=0,"Müddət və qiyməti düzgün yazın.");
   if("CHAT".equals(a.getKind())){
     require(Set.of("MOTO","SUSHI").contains(Objects.toString(form.channel(),"")),"Çat bölməsini seçin.");
     if("WHATSAPP".equals(form.targetType())) a.setTarget(phone(form.target()));
     else {require("LINK".equals(form.targetType())&&validLink(form.target()),"HTTPS keçidini düzgün yazın.");a.setTarget(form.target());}
     a.setChannel(form.channel());a.setTargetType(form.targetType());
   }
   a.setTitle(clean(form.title()));a.setDescription(clean(form.description()));a.setImageUrl(clean(form.imageUrl()));
   a.setPrice(form.price());a.setDurationHours(form.durationHours());a.setPlanLabel(form.durationHours()/24+" gün");
   // Editing an active advertisement never restarts its paid publication period.
   if(a.getStartsAt()!=null)a.setEndsAt(a.getStartsAt()+form.durationHours()*3600000L);
   requests.saveAndFlush(a);changed();return view(a);
 }
 @Transactional public void deleteRequest(Long id){
   var a=requests.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Reklam tapılmadı."));
   if("DELETED".equals(a.getStatus()))return;
   a.setStatus("DELETED");requests.saveAndFlush(a);changed();
 }
 @Transactional public Map<String,Object> moderate(Long id,String action){var a=requests.lock(id).orElseThrow();require(!"DELETED".equals(a.getStatus()),"Reklam silinib.");require(Set.of("approve","reject","withdraw").contains(action),"Əməliyyatı yoxlayın.");if("approve".equals(action)){require(!"REJECTED".equals(a.getStatus())&&!"CANCELLED".equals(a.getStatus()),"Rədd edilmiş sorğu aktivləşdirilə bilməz.");if(!"APPROVED".equals(a.getStatus())){if("SHOP".equals(a.getKind())){
     require(listings.lockForPromotion(a.getListingId()).map(e->!Boolean.TRUE.equals(e.getIsDisable())).orElse(false),"Elan aktiv deyil.");
     require(requests.openPromotions(a.getUserId(),a.getListingId(),System.currentTimeMillis()).stream().noneMatch(other->!other.getId().equals(id)),"Bu elan üçün artıq aktiv reklam sorğusu var.");
   }long start=System.currentTimeMillis();a.setStartsAt(start);a.setEndsAt(start+a.getDurationHours()*3600000L);a.setStatus("APPROVED");}}else {if("reject".equals(action))require("PENDING".equals(a.getStatus()),"Yalnız gözləyən sorğu rədd edilə bilər.");else require("APPROVED".equals(a.getStatus())&&a.getEndsAt()!=null&&a.getEndsAt()>System.currentTimeMillis(),"Reklam artıq aktiv deyil.");a.setStatus("reject".equals(action)?"REJECTED":"CANCELLED");}requests.saveAndFlush(a);changed();return view(a);}
}

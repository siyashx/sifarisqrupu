package com.codesupreme.sifarisqrupu.lottery;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.push.FcmPushService;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.net.URI;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class LotteryService {
    private final LotteryRepository lotteries;
    private final LotteryTicketRepository tickets;
    private final UserRepository users;
    private final LotteryUserDirectory directory;
    private final FcmPushService fcm;
    private final SimpMessagingTemplate events;
    private final SecureRandom random=new SecureRandom();
    private static final ZoneId BAKU=ZoneId.of("Asia/Baku");
    public LotteryService(LotteryRepository lotteries,LotteryTicketRepository tickets,UserRepository users,
                          LotteryUserDirectory directory,FcmPushService fcm,SimpMessagingTemplate events) {
        this.lotteries=lotteries;this.tickets=tickets;this.users=users;this.directory=directory;this.fcm=fcm;this.events=events;
    }
    public record Form(String title,String description,String imageUrl,Long drawAt,BigDecimal ticketPrice,Integer totalTickets) {}
    public record Issue(List<Long> userIds,String requestId) {}
    public record Open(boolean notifyParticipants,boolean confirmEarly,int durationSeconds) {}
    public record Person(Long id,String name,String phoneNumber) {}
    public record TicketView(String code,Person user,long createdAt) {}
    public record View(Long id,long version,String title,String description,String imageUrl,long drawAt,
        BigDecimal ticketPrice,int totalTickets,int soldTickets,double soldPercent,String status,int durationSeconds,
        Long startedAt,Long endsAt,Double finalRotation,String winnerTicket,String winnerName,Person winner,
        List<String> myTickets,long serverNow,long createdAt,Notice notification,List<String> wheelTickets) {}
    public record Notice(int queued,int participants,boolean fcmEnabled) {}
    public record PageView<T>(List<T> items,long total,int page,int pages) {}

    private Lottery visible(Lottery l) {if(l.isDeleted()) throw error(HttpStatus.NOT_FOUND,"Lotoreya silinib və ya mövcud deyil.");return l;}
    private Lottery locked(Long id) {return visible(lotteries.lock(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Lotoreya tapılmadı.")));}
    private static ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
    private static void require(boolean condition,String message) {if(!condition) throw error(HttpStatus.CONFLICT,message);}
    private static String clean(String value) {return value==null ? "" : value.strip();}
    private static boolean early(Lottery l) {return LocalDate.now(BAKU).isBefore(Instant.ofEpochMilli(l.getDrawAt()).atZone(BAKU).toLocalDate());}
    private static Person person(User u) {return new Person(u.getId(),Objects.toString(u.getName(),"İstifadəçi"),Objects.toString(u.getPhoneNumber(),""));}
    private static String code(int value) {return String.format(Locale.ROOT,"%05d",value);}
    private void change(Lottery l) {
        l.setUpdatedAt(System.currentTimeMillis());lotteries.saveAndFlush(l);
        // Broadcast invalidation only. Never publish personal tickets or phone numbers.
        Runnable send=()->events.convertAndSend("/topic/lotteries",Map.of("lotteryId",l.getId(),"updatedAt",l.getUpdatedAt()));
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){send.run();}});
        } else send.run();
    }
    private void apply(Lottery l,Form f) {
        if(f==null || clean(f.title()).isEmpty() || clean(f.title()).length()>140 || clean(f.description()).length()>4000 ||
            clean(f.imageUrl()).length()>2000 || f.drawAt()==null || f.drawAt()<=0 || f.ticketPrice()==null ||
            f.ticketPrice().signum()<0 || f.ticketPrice().scale()>2 || f.ticketPrice().compareTo(new BigDecimal("99999999"))>0 ||
            f.totalTickets()==null || f.totalTickets()<1 || f.totalTickets()>99999) throw error(HttpStatus.BAD_REQUEST,"Məlumatları düzgün doldurun. Bilet sayı 1–99999 olmalıdır.");
        try {var uri=URI.create(f.imageUrl());if(!"https".equals(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException();}
        catch(RuntimeException e) {throw error(HttpStatus.BAD_REQUEST,"Şəkil üçün HTTPS keçidi tələb olunur.");}
        require(f.totalTickets()>=l.getSoldTickets(),"Toplam bilet sayı verilmiş biletlərdən az ola bilməz.");
        l.setTitle(clean(f.title()));l.setDescription(clean(f.description()));l.setImageUrl(clean(f.imageUrl()));
        l.setDrawAt(f.drawAt());l.setTicketPrice(f.ticketPrice());l.setTotalTickets(f.totalTickets());
    }
    private View view(Lottery l,Long userId,boolean admin) { return view(l,userId,admin,null); }
    private View view(Lottery l,Long userId,boolean admin,Notice notification) {return view(l,userId,admin,notification,true);}
    private View view(Lottery l,Long userId,boolean admin,Notice notification,boolean includeWheel) {
        User winner=null;
        if("COMPLETED".equals(l.getStatus()) && l.getWinnerNumber()!=null) {
            winner=tickets.findByLotteryIdAndNumber(l.getId(),l.getWinnerNumber()).flatMap(t->users.findById(t.getUserId())).orElse(null);
        }
        var own=userId==null ? List.<String>of() : tickets.findByLotteryIdAndUserIdAndRevokedFalseOrderByNumber(l.getId(),userId).stream().map(LotteryTicket::code).toList();
        return new View(l.getId(),l.getVersion(),l.getTitle(),l.getDescription(),l.getImageUrl(),l.getDrawAt(),l.getTicketPrice(),l.getTotalTickets(),l.getSoldTickets(),
            l.getTotalTickets()==0 ? 0 : l.getSoldTickets()*100.0/l.getTotalTickets(),l.getStatus(),l.getDurationSeconds(),l.getStartedAt(),l.getEndsAt(),l.getFinalRotation(),
            "COMPLETED".equals(l.getStatus()) ? code(l.getWinnerNumber()) : null,winner==null ? null : winner.getName(),admin && winner!=null ? person(winner):null,own,System.currentTimeMillis(),l.getCreatedAt(),notification,includeWheel && Set.of("LIVE","DRAWING","COMPLETED").contains(l.getStatus()) ? tickets.findByLotteryIdAndRevokedFalseOrderByNumber(l.getId()).stream().map(LotteryTicket::code).toList() : List.of());
    }
    @Transactional(readOnly=true) public List<View> list(Long user,boolean admin) {
        return lotteries.findByDeletedFalseOrderByCreatedAtDesc().stream().filter(l->admin || !"CANCELLED".equals(l.getStatus())).map(l->view(l,user,admin,null,false)).toList();
    }
    @Transactional(readOnly=true) public View detail(Long id,Long user,boolean admin) {
        var l=visible(lotteries.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Lotoreya tapılmadı.")));return view(l,user,admin);
    }
    @Transactional public View create(Form form,Long admin) {
        var l=new Lottery();apply(l,form);l.setCreatedBy(admin);l.setCreatedAt(System.currentTimeMillis());change(l);return view(l,null,true);
    }
    @Transactional public View edit(Long id,Form form) {
        var l=locked(id);require("ACTIVE".equals(l.getStatus()),"Başlamış və ya bitmiş lotoreya dəyişdirilə bilməz.");
        apply(l,form);change(l);return view(l,null,true);
    }
    @Transactional public View cancel(Long id) {
        var l=locked(id);require(Set.of("ACTIVE","LIVE").contains(l.getStatus()),"Fırlanma başladıqdan sonra lotoreya ləğv edilə bilməz.");
        l.setStatus("CANCELLED");change(l);return view(l,null,true);
    }
    @Transactional public List<String> issue(Long id,Issue request,Long admin) {
        var l=locked(id);
        if(request==null || request.userIds()==null || request.userIds().isEmpty() || request.userIds().size()>100 ||
            request.requestId()==null || !request.requestId().matches("[a-zA-Z0-9-]{16,64}")) throw error(HttpStatus.BAD_REQUEST,"İstifadəçi seçin.");
        var ids=new LinkedHashSet<>(request.userIds());
        if(ids.contains(null)) throw error(HttpStatus.BAD_REQUEST,"Yanlış istifadəçi.");
        var old=tickets.findByLotteryIdAndRequestIdOrderByNumber(id,request.requestId());
        if(!old.isEmpty()) {
            require(old.stream().map(LotteryTicket::getUserId).collect(java.util.stream.Collectors.toSet()).equals(ids),"Sorğu identifikatoru artıq istifadə edilib.");
            require(old.stream().noneMatch(LotteryTicket::isRevoked),"Bu sorğunun bileti geri alınıb. Yeni bilet vermə əməliyyatı başladın.");
            return old.stream().map(LotteryTicket::code).toList();
        }
        require("ACTIVE".equals(l.getStatus()),"Bilet verilməsi bağlanıb.");
        require(l.getSoldTickets()+ids.size()<=l.getTotalTickets(),"Kifayət qədər boş bilet yoxdur.");
        var people=users.findAllById(ids);
        require(people.size()==ids.size() && people.stream().noneMatch(u->Boolean.TRUE.equals(u.getIsDisable())),"İstifadəçi tapılmadı və ya deaktivdir.");
        int next=tickets.findFirstByLotteryIdOrderByNumberDesc(id).map(LotteryTicket::getNumber).orElse(0);
        require(next+ids.size()<=99999,"Bu lotoreya üçün bilet kodu limiti dolub.");
        var issued=new ArrayList<LotteryTicket>();
        for(Long user:ids) {
            var ticket=new LotteryTicket();ticket.setLotteryId(id);ticket.setNumber(++next);ticket.setUserId(user);
            ticket.setRequestId(request.requestId());ticket.setIssuedBy(admin);ticket.setCreatedAt(System.currentTimeMillis());
            tickets.save(ticket);issued.add(ticket);l.setSoldTickets(l.getSoldTickets()+1);
        }
        change(l);return issued.stream().map(LotteryTicket::code).toList();
    }
    @Transactional public View revokeTicket(Long id,String ticketCode,Long admin) {
        var l=locked(id);
        require("ACTIVE".equals(l.getStatus()),"Bilet yalnız lotoreya canlı başlamazdan əvvəl geri alına bilər.");
        if(ticketCode==null || !ticketCode.matches("[0-9]{5}") || Integer.parseInt(ticketCode)<1)
            throw error(HttpStatus.BAD_REQUEST,"Bilet kodu düzgün deyil.");
        var ticket=tickets.findByLotteryIdAndNumber(id,Integer.parseInt(ticketCode)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Bilet tapılmadı."));
        if(ticket.isRevoked()) return view(l,null,true);
        ticket.setRevoked(true);ticket.setRevokedBy(admin);ticket.setRevokedAt(System.currentTimeMillis());tickets.saveAndFlush(ticket);
        l.setSoldTickets(l.getSoldTickets()-1);change(l);return view(l,null,true);
    }
    @Transactional public void delete(Long id,Long admin) {
        var l=lotteries.lock(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Lotoreya tapılmadı."));
        if(l.isDeleted()) return;
        require(!"DRAWING".equals(l.getStatus()),"Çarx fırlanarkən lotoreya silinə bilməz. Nəticəni gözləyin.");
        l.setDeleted(true);l.setDeletedBy(admin);l.setDeletedAt(System.currentTimeMillis());change(l);
    }
    @Transactional public View open(Long id,Open request) {
        var l=locked(id);require(Set.of("ACTIVE","LIVE").contains(l.getStatus()),"Bu lotoreyanın çarxı artıq başladılıb və ya bağlanıb.");
        require(l.getSoldTickets()>0,"Əvvəlcə ən azı bir bilet verin.");
        if(request.durationSeconds()<5 || request.durationSeconds()>3600) throw error(HttpStatus.BAD_REQUEST,"Müddət 5–3600 saniyə olmalıdır.");
        require(!early(l) || request.confirmEarly(),"Çarxı lotoreya tarixindən əvvəl çevirməkdən əminsiniz?");
        l.setStatus("LIVE");l.setDurationSeconds(request.durationSeconds());change(l);
        Notice notification=request.notifyParticipants() ? notifyLocked(l) : null;
        return view(l,null,true,notification);
    }
    @Transactional public View spin(Long id) {
        var l=locked(id);require("LIVE".equals(l.getStatus()),"Çarx artıq fırladılıb və ya canlı rejim açılmayıb.");
        var eligible=tickets.findByLotteryIdAndRevokedFalseOrderByNumber(id);
        require(!eligible.isEmpty() && eligible.size()==l.getSoldTickets(),"İştirakçı biletləri yenidən yoxlayın.");
        int winnerIndex=random.nextInt(eligible.size());
        l.setWinnerNumber(eligible.get(winnerIndex).getNumber());
        long now=System.currentTimeMillis();l.setStartedAt(now+2000);l.setEndsAt(l.getStartedAt()+l.getDurationSeconds()*1000L);
        double step=2*Math.PI/l.getSoldTickets();
        double landing=(2*Math.PI-winnerIndex*step)%(2*Math.PI);
        // Whole revolutions are essential: the pointer must end on the winner.
        l.setFinalRotation(2*Math.PI*Math.max(8,(int)Math.ceil(l.getDurationSeconds()/3.0))+landing);
        l.setStatus("DRAWING");change(l);return view(l,null,true);
    }
    @Transactional public Notice notifyParticipants(Long id) {
        var l=locked(id);require(Set.of("LIVE","DRAWING").contains(l.getStatus()),"Lotoreya hazırda canlı deyil.");return notifyLocked(l);
    }
    /** Admin-confirmed announcement, including customers without tickets or subscriptions. */
    @Transactional public Notice announce(Long id) {
        var l=locked(id);require("ACTIVE".equals(l.getStatus()),"Yeni lotoreya elanı yalnız aktiv lotoreya üçün göndərilə bilər.");
        var recipients=users.findAll().stream().filter(u->u.getId()!=null && !Boolean.TRUE.equals(u.getIsDisable()))
            .map(User::getId).distinct().toList();
        l.setNotificationRound(l.getNotificationRound()+1);lotteries.save(l);
        int queued=0;
        for(String app:List.of("ZAKAZ","ELEHBER")) queued+=fcm.enqueue(app,recipients,"🎟️ Yeni lotoreya!",
            l.getTitle()+" — Lotoreya ilə tanış olmaq üçün bildirişə toxunun.",
            Map.of("scope","lottery","channel","lottery","lotteryId",l.getId(),"event","created"),86400,
            "lottery-"+l.getId()+"-created-"+l.getNotificationRound());
        return new Notice(queued,recipients.size(),fcm.isEnabled());
    }
    private Notice notifyLocked(Lottery l) {
        l.setNotificationRound(l.getNotificationRound()+1);lotteries.save(l);
        var participants=new LinkedHashSet<Long>();
        int page=0;Page<LotteryTicket> rows;
        do {rows=tickets.findByLotteryIdAndRevokedFalseOrderByNumber(l.getId(),PageRequest.of(page++,500));rows.forEach(t->participants.add(t.getUserId()));} while(rows.hasNext());
        int queued=0;
        for(String app:List.of("ZAKAZ","ELEHBER")) queued+=fcm.enqueue(app,participants,"🔴 Lotoreya hazırda keçirilir",l.getTitle()+" — Lotoreyanı canlı izləmək üçün bildiriş üzərinə klik edin.",
            Map.of("scope","lottery","channel","lottery","lotteryId",l.getId(),"event","live"),300,"lottery-"+l.getId()+"-live-"+l.getNotificationRound());
        return new Notice(queued,participants.size(),fcm.isEnabled());
    }
    @Transactional public void finish(Long id) {
        var l=locked(id);
        if(!"DRAWING".equals(l.getStatus()) || l.getEndsAt()>System.currentTimeMillis()) return;
        l.setStatus("COMPLETED");change(l);
        var winner=tickets.findByLotteryIdAndNumber(id,l.getWinnerNumber()).orElseThrow();
        // Aggregate by participant: one notification for ordinary purchases,
        // with bounded chunks for unusually large ticket collections.
        var participants=new TreeMap<Long,List<String>>();
        int page=0;Page<LotteryTicket> rows;
        do {
            rows=tickets.findByLotteryIdAndRevokedFalseOrderByNumber(id,PageRequest.of(page++,500));
            rows.forEach(t->participants.computeIfAbsent(t.getUserId(),ignored->new ArrayList<>()).add(t.code()));
        } while(rows.hasNext());
        for(var participant:participants.entrySet()) {
            Long userId=participant.getKey();boolean won=Objects.equals(userId,winner.getUserId());
            var codes=won ? List.of(winner.code()) : participant.getValue();
            for(int offset=0;offset<codes.size();offset+=30) {
                String batch=String.join(", ",codes.subList(offset,Math.min(offset+30,codes.size())));
                String body=won ? "Təbriklər! "+l.getTitle()+" lotoreyasında "+batch+" kodlu biletiniz qalib oldu!" :
                    l.getTitle()+": Lotoreya keçirildi. "+batch+(Math.min(offset+30,codes.size())-offset==1 ? " kodlu biletiniz" : " kodlu biletləriniz")+" təəssüf ki, qalib olmadı. Şansınızı digər lotoreyalarda yenidən yoxlaya bilərsiniz.";
                for(String app:List.of("ZAKAZ","ELEHBER")) fcm.enqueue(app,List.of(userId),won ? "🎉 Qalib oldunuz!" : "Lotoreyanın nəticəsi",body,
                    Map.of("scope","lottery","channel","lottery","lotteryId",id,"event",won ? "won":"lost","ticketCode",codes.get(offset),"ticketCodes",batch),86400,
                    "lottery-"+id+"-result-"+userId+"-"+(offset/30));
            }
        }
    }
    @Transactional(readOnly=true) public PageView<Person> people(String query,int page) {
        var result=directory.search(clean(query).toLowerCase(Locale.ROOT),PageRequest.of(Math.max(0,page),50));
        return new PageView<>(result.map(LotteryService::person).getContent(),result.getTotalElements(),result.getNumber(),result.getTotalPages());
    }
    @Transactional(readOnly=true) public PageView<TicketView> ticketPage(Long id,int page) {
        visible(lotteries.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Lotoreya tapılmadı.")));
        var result=tickets.findByLotteryIdAndRevokedFalseOrderByNumber(id,PageRequest.of(Math.max(0,page),50));
        var people=new HashMap<Long,Person>();users.findAllById(result.stream().map(LotteryTicket::getUserId).distinct().toList()).forEach(u->people.put(u.getId(),person(u)));
        return new PageView<>(result.stream().map(t->new TicketView(t.code(),people.getOrDefault(t.getUserId(),new Person(t.getUserId(),"Silinmiş istifadəçi","")),t.getCreatedAt())).toList(),result.getTotalElements(),result.getNumber(),result.getTotalPages());
    }
}

package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dao.chat_group.ChatGroupRepository;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PushAudienceService {
    private final UserRepository users;
    private final ChatGroupRepository groups;
    private final NotificationMutePreferenceService mutes;
    public PushAudienceService(UserRepository users, ChatGroupRepository groups, NotificationMutePreferenceService mutes) {
        this.users=users; this.groups=groups; this.mutes=mutes;
    }
    public List<Long> select(String app, String kind, Long sender, Collection<Long> requested, boolean bridge) {
        Set<Long> muted=new HashSet<>();
        if("shop".equals(kind)) {
            groups.findById(3L).ifPresent(g->{if(g.getMutedUserIds()!=null) muted.addAll(g.getMutedUserIds());});
        } else if(!"notice".equals(kind)) {
            muted.addAll(mutes.getEffectiveMutedUserIds(app,"ELEHBER".equals(app) ? "MOTOTAXI_CHAT" : "ORDER_GROUP_PUSH"));
        }
        Set<Long> restriction=requested==null ? null : new HashSet<>(requested);
        long now=System.currentTimeMillis();
        return users.findAll().stream().filter(u->u.getId()!=null && !u.getId().equals(sender))
            .filter(u->!Boolean.TRUE.equals(u.getIsDisable()) && !muted.contains(u.getId()))
            .filter(u->restriction==null || restriction.contains(u.getId()))
            .filter(u->("shop".equals(kind) || "notice".equals(kind)) || u.getUserType()==null || !u.getUserType().toLowerCase(Locale.ROOT).contains("customer"))
            .filter(u->"notice".equals(kind) || !"ELEHBER".equals(app) || Boolean.TRUE.equals(u.getOnline()))
            // Preserve mobile Zakaz subscription filtering. Bridge historically sends to all non-muted couriers.
            .filter(u->bridge || !"ZAKAZ".equals(app) || "shop".equals(kind) ||
                (Boolean.TRUE.equals(u.getIsSub()) && u.getExpiryDate()!=null && u.getExpiryDate().getTime()>now))
            .map(u->u.getId()).toList();
    }
    public static Map<String,Object> payload(String app,String kind) {
        return switch(kind) {
            case "shop" -> Map.of("screen","ShopGroup","groupId",3,"channel","shop");
            case "group" -> Map.of("screen","OrderGroup","groupId",0,"channel","group");
            case "notice", "moto_chat" -> Map.of("screen","ELEHBER".equals(app) ? "MotoTaksiChat":"MotoTaksi",
                "scope","mototaxi_chat","groupId",1,"channel","moto_chat",
                "route","ELEHBER".equals(app) ? "/moto-taksi-chat":"/moto-taksi");
            default -> throw new IllegalArgumentException("Unsupported notification kind");
        };
    }
}

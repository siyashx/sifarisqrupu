package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.service.impl.notification_mute.NotificationMutePreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

@Service
public class PushDeliveryPolicy {
    private final OrderRepository orders;
    private final UserRepository users;
    private final NotificationMutePreferenceService mutes;
    public PushDeliveryPolicy(OrderRepository orders,UserRepository users,NotificationMutePreferenceService mutes) {
        this.orders=orders;this.users=users;this.mutes=mutes;
    }
    @Transactional(readOnly=true)
    public boolean allows(Map<String,String> data,Long userId,String app) {
        // These preferences belong to incoming work, never to lotteries,
        // the marketplace, personal order updates, or stop controls.
        if(isWorkAlert(data)) {
            if("ELEHBER".equals(app)) {
                if(!users.findById(userId).map(u->Boolean.TRUE.equals(u.getOnline())).orElse(false)) return false;
                if(!"mototaxi".equals(data.get("scope")) && Boolean.TRUE.equals(mutes.getStatus(userId,app,"MOTOTAXI_CHAT").getMuted())) return false;
            } else if("ZAKAZ".equals(app) && Boolean.TRUE.equals(mutes.getStatus(userId,app,"ORDER_GROUP_PUSH").getMuted())) {
                return false;
            }
        }
        return allows(data,userId);
    }
    static boolean isWorkAlert(Map<String,String> data) {
        if("lottery".equals(data.get("scope")) || "lottery".equals(data.get("channel")) ||
            "shop".equals(data.get("channel")) || "notice".equals(data.get("event"))) return false;
        if("mototaxi".equals(data.get("scope"))) return "new_order".equals(data.get("event"));
        return "new_order".equals(data.get("event")) || "group".equals(data.get("channel")) ||
            "group".equals(data.get("scope")) || "0".equals(data.get("groupId")) ||
            "mototaxi_chat".equals(data.get("scope")) || "moto_chat".equals(data.get("channel")) || "1".equals(data.get("groupId"));
    }
    /** Checked both when queueing and immediately before APNs/FCM delivery. */
    public static boolean allowsDevice(Map<String,String> data, PushDevice device) {
        String app=device.getAppCode();
        if (!"ELEHBER".equals(app) && !"ZAKAZ".equals(app)) return true;
        boolean group="group".equals(data.get("channel")) || "group".equals(data.get("scope")) || "0".equals(data.get("groupId"));
        // Sushi/OrderGroup has never been part of Elehber's push audience.
        if ("ELEHBER".equals(app) && group) return false;
        // Legacy releases do not report a local role; preserve their delivery
        // until updated. Current clients sync the role on login and every switch.
        if (!"customer".equals(device.getActiveRole())) return true;
        // Personal ride/delivery updates still reach the customer.
        if ("mototaxi".equals(data.get("scope")) && !"new_order".equals(data.get("event"))) return true;
        return !group && !"new_order".equals(data.get("event"))
            && !"mototaxi_chat".equals(data.get("scope")) && !"moto_chat".equals(data.get("channel"))
            && !"1".equals(data.get("groupId"));
    }
    @Transactional(readOnly=true)
    public boolean allows(Map<String,String> data, Long userId) {
        if(!"new_order".equals(data.get("event")) || !"mototaxi".equals(data.get("scope"))) return true;
        try {
            var order=orders.findById(Long.parseLong(data.get("orderId"))).orElse(null);
            if(order==null || Objects.equals(order.getCustomerId(),userId) || Boolean.TRUE.equals(order.getIsDisable()) || !"no_courier".equals(order.getStatus())) return false;
            long now=System.currentTimeMillis();
            var offers=order.getActiveOfferExpirations();
            if(offers!=null && offers.containsKey(userId)) return offers.get(userId).getTime()>now;
            return Objects.equals(order.getOfferedCourierId(),userId) && order.getOfferExpiresAt()!=null && order.getOfferExpiresAt().getTime()>now;
        } catch(NumberFormatException error) {return false;}
    }
}

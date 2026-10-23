package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

@Service
public class PushDeliveryPolicy {
    private final OrderRepository orders;
    public PushDeliveryPolicy(OrderRepository orders) {this.orders=orders;}
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

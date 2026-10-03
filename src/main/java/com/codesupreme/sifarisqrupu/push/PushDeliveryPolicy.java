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
    @Transactional(readOnly=true)
    public boolean allows(Map<String,String> data, Long userId) {
        if(!"new_order".equals(data.get("event")) || !"mototaxi".equals(data.get("scope"))) return true;
        try {
            var order=orders.findById(Long.parseLong(data.get("orderId"))).orElse(null);
            if(order==null || Boolean.TRUE.equals(order.getIsDisable()) || !"no_courier".equals(order.getStatus())) return false;
            long now=System.currentTimeMillis();
            var offers=order.getActiveOfferExpirations();
            if(offers!=null && offers.containsKey(userId)) return offers.get(userId).getTime()>now;
            return Objects.equals(order.getOfferedCourierId(),userId) && order.getOfferExpiresAt()!=null && order.getOfferExpiresAt().getTime()>now;
        } catch(NumberFormatException error) {return false;}
    }
}

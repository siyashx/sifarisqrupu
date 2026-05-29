package com.codesupreme.sifarisqrupu.dao.order;

import com.codesupreme.sifarisqrupu.model.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Query("""
        UPDATE Order o
        SET o.isDisable = true
        WHERE o.status = :status
        AND o.createdAt <= :createdAt
        AND o.isDisable = false
    """)
    int disableExpiredNoCourierOrders(String status, Date createdAt);
}
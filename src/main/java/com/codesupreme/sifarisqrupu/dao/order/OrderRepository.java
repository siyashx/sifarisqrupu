package com.codesupreme.sifarisqrupu.dao.order;

import com.codesupreme.sifarisqrupu.model.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
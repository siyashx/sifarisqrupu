package com.codesupreme.sifarisqrupu.api.order.controller;


import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.service.impl.order.OrderServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/order")
public class OrderController {

    private final OrderServiceImpl service;
    public OrderController(OrderServiceImpl service) {this.service = service;}

    @GetMapping
    public ResponseEntity<List<OrderDto>> getAllOrder() {
        List<OrderDto> all = service.getAllOrder();
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable("orderId") Long id) {
        OrderDto det = service.getOrderById(id);
        if (det != null) {
            return ResponseEntity.ok(det);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto dto) {
        OrderDto created = service.createOrder(dto);
        return ResponseEntity.ok(created);
    }

    // Update
    @PutMapping("/{orderId}")
    public ResponseEntity<?> updateOrder(
            @PathVariable("orderId") Long id,
            @RequestBody OrderDto orderDto) {
        OrderDto updatedOrder = service.updateOrder(id, orderDto);
        if (updatedOrder != null) {
            return ResponseEntity.ok(updatedOrder);
        }
        return ResponseEntity.notFound().build();
    }


    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable("orderId") Long id) {
        Boolean deleted = service.deleteOrder(id);
        if (deleted) {
            return ResponseEntity.ok("Admin order deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}

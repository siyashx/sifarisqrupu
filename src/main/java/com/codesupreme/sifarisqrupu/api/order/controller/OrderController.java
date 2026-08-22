package com.codesupreme.sifarisqrupu.api.order.controller;

import com.codesupreme.sifarisqrupu.dto.order.CourierOrderActionRequest;
import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.service.impl.order.MotoTaxiOrderDispatchService;
import com.codesupreme.sifarisqrupu.service.impl.order.OrderServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/order")
public class OrderController {

    private final OrderServiceImpl service;
    private final MotoTaxiOrderDispatchService dispatchService;

    public OrderController(
            OrderServiceImpl service,
            MotoTaxiOrderDispatchService dispatchService
    ) {
        this.service = service;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getAllOrder() {
        return ResponseEntity.ok(service.getAllOrder());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable("orderId") Long id) {
        OrderDto det = service.getOrderById(id);
        return det != null ? ResponseEntity.ok(det) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto dto) {
        OrderDto created = service.createOrder(dto);

        // The order is created even when nobody is online. Dispatch either reserves
        // the nearest courier immediately or leaves it open for scheduler retries.
        dispatchService.processOrder(created.getId());

        OrderDto fresh = service.getOrderById(created.getId());
        return ResponseEntity.ok(fresh != null ? fresh : created);
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<OrderDto> acceptOrder(
            @PathVariable("orderId") Long orderId,
            @RequestBody CourierOrderActionRequest request
    ) {
        return ResponseEntity.ok(dispatchService.acceptOrder(orderId, request.getCourierId()));
    }

    @PostMapping("/{orderId}/decline")
    public ResponseEntity<OrderDto> declineOrder(
            @PathVariable("orderId") Long orderId,
            @RequestBody CourierOrderActionRequest request
    ) {
        return ResponseEntity.ok(dispatchService.declineOffer(orderId, request.getCourierId()));
    }

    @PostMapping("/{orderId}/courier-cancel")
    public ResponseEntity<OrderDto> courierCancel(
            @PathVariable("orderId") Long orderId,
            @RequestBody CourierOrderActionRequest request
    ) {
        return ResponseEntity.ok(dispatchService.cancelAcceptedOrder(orderId, request.getCourierId()));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDto> customerCancel(@PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(dispatchService.cancelOrder(orderId));
    }

    /**
     * Generic update is retained for older app versions and normal lifecycle
     * updates (on_the_way/completed). Dispatch-sensitive legacy payloads are
     * routed through the atomic dispatch endpoints instead of bypassing them.
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<?> updateOrder(
            @PathVariable("orderId") Long id,
            @RequestBody OrderDto orderDto
    ) {
        OrderDto existing = service.getOrderById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        if (Boolean.TRUE.equals(orderDto.getIsDisable())) {
            return ResponseEntity.ok(dispatchService.cancelOrder(id));
        }

        Long incomingCourierId = orderDto.getCourierId();
        boolean incomingAssignment = incomingCourierId != null
                && incomingCourierId > 0
                && "to_customer".equals(orderDto.getStatus());

        if ("no_courier".equals(existing.getStatus()) && incomingAssignment) {
            return ResponseEntity.ok(dispatchService.acceptOrder(id, incomingCourierId));
        }

        if ("no_courier".equals(existing.getStatus())
                && existing.getOfferedCourierId() != null
                && orderDto.getCancelledCourierIds() != null
                && orderDto.getCancelledCourierIds().contains(existing.getOfferedCourierId())) {
            return ResponseEntity.ok(
                    dispatchService.declineOffer(id, existing.getOfferedCourierId())
            );
        }

        boolean legacyCourierCancel = existing.getCourierId() != null
                && ("to_customer".equals(existing.getStatus()) || "on_the_way".equals(existing.getStatus()))
                && "no_courier".equals(orderDto.getStatus())
                && Long.valueOf(0L).equals(orderDto.getCourierId());

        if (legacyCourierCancel) {
            return ResponseEntity.ok(
                    dispatchService.cancelAcceptedOrder(id, existing.getCourierId())
            );
        }

        OrderDto updatedOrder = service.updateOrder(id, orderDto);
        return updatedOrder != null
                ? ResponseEntity.ok(updatedOrder)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> deleteOrder(@PathVariable("orderId") Long id) {
        Boolean deleted = service.deleteOrder(id);
        return deleted
                ? ResponseEntity.ok("Admin order deleted successfully")
                : ResponseEntity.notFound().build();
    }
}

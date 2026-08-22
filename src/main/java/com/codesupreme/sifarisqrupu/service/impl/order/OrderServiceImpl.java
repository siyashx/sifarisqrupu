package com.codesupreme.sifarisqrupu.service.impl.order;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiPricingService;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class OrderServiceImpl {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;
    private final MotoTaxiPricingService pricingService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ModelMapper modelMapper,
            MotoTaxiPricingService pricingService
    ) {
        this.orderRepository = orderRepository;
        this.modelMapper = modelMapper;
        this.pricingService = pricingService;
    }

    //ALL
    public List<OrderDto> getAllOrder() {
        List<Order> list = orderRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, OrderDto.class))
                .toList();
    }

    //ById
    public OrderDto getOrderById(Long id) {
        Optional<Order> optional = orderRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, OrderDto.class)).orElse(null);
    }

    //Create
    @Transactional
    public OrderDto createOrder(OrderDto dto) {
        Order det = modelMapper.map(dto, Order.class);

        if (det.getIsDisable() == null) {
            det.setIsDisable(false);
        }

        // Price is always calculated by backend. Any price sent by the client is ignored.
        det.setPrice(pricingService.calculateOrderPrice(
                det.getCustomerId(),
                det.getOrderType(),
                det.getDistance()
        ));

        det = orderRepository.save(det);
        return modelMapper.map(det, OrderDto.class);
    }

    @Transactional
    public void disableNoCourierOrdersAfterTenMinutes() {
        Date tenMinutesAgo = new Date(System.currentTimeMillis() - 10 * 60 * 1000);

        orderRepository.disableExpiredNoCourierOrders("no_courier", tenMinutesAgo);
    }

    //Update
    @Transactional
    public OrderDto updateOrder(Long orderId, OrderDto orderDto) {
        Optional<Order> optional = orderRepository.findById(orderId);
        if (optional.isPresent()) {
            Order order = optional.get();
            boolean pricingInputsChanged = false;

            if (orderDto.getCourierId() != null) {
                if (orderDto.getCourierId() == 0) {
                    order.setCourierId(null); // Explicit null təyin et
                } else {
                    order.setCourierId(orderDto.getCourierId());
                }
            }

            if (orderDto.getCustomerId() != null
                    && !Objects.equals(order.getCustomerId(), orderDto.getCustomerId())) {
                order.setCustomerId(orderDto.getCustomerId());
                pricingInputsChanged = true;
            }

            if (orderDto.getOrderType() != null
                    && !Objects.equals(order.getOrderType(), orderDto.getOrderType())) {
                order.setOrderType(orderDto.getOrderType());
                pricingInputsChanged = true;
            }

            if (orderDto.getFromAddress() != null) {
                order.setFromAddress(orderDto.getFromAddress());
            }

            if (orderDto.getToAddress() != null) {
                order.setToAddress(orderDto.getToAddress());
            }

            if (orderDto.getCancelledCourierIds() != null) {
                order.setCancelledCourierIds(orderDto.getCancelledCourierIds());
            }

            if (orderDto.getStatus() != null) {
                order.setStatus(orderDto.getStatus());
            }

            if (orderDto.getDistance() != null
                    && !Objects.equals(order.getDistance(), orderDto.getDistance())) {
                order.setDistance(orderDto.getDistance());
                pricingInputsChanged = true;
            }

            if (orderDto.getIsDisable() != null) {
                order.setIsDisable(orderDto.getIsDisable());
            }

            // Client cannot overwrite price directly. Recalculate only when a pricing input
            // actually changes, so an admin price update never changes an in-progress order.
            if (pricingInputsChanged) {
                order.setPrice(pricingService.calculateOrderPrice(
                        order.getCustomerId(),
                        order.getOrderType(),
                        order.getDistance()
                ));
            }

            order = orderRepository.save(order);

            return modelMapper.map(order, OrderDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteOrder(Long id) {
        Optional<Order> optional = orderRepository.findById(id);
        if (optional.isPresent()) {
            Order det = optional.get();
            orderRepository.delete(det);
            return true;
        }
        return false;
    }


}



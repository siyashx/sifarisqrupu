package com.codesupreme.sifarisqrupu.service.impl.order;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.model.order.Order;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl {

    private final OrderRepository orderRepository;
    private final ModelMapper modelMapper;

    public OrderServiceImpl(OrderRepository orderRepository, ModelMapper modelMapper) {
        this.orderRepository = orderRepository;
        this.modelMapper = modelMapper;
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
    public OrderDto createOrder(OrderDto dto) {
        Order det = modelMapper.map(dto, Order.class);
        det = orderRepository.save(det);
        return modelMapper.map(det, OrderDto.class);
    }

    //Update
    public OrderDto updateOrder(Long orderId, OrderDto orderDto) {
        Optional<Order> optional = orderRepository.findById(orderId);
        if (optional.isPresent()) {
            Order order = optional.get();


            if (orderDto.getCourierId() != null) {
                order.setCourierId(orderDto.getCourierId());
            }

            if (orderDto.getCustomerId() != null) {
                order.setCustomerId(orderDto.getCustomerId());
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

            if (orderDto.getPrice() != null) {
                order.setPrice(orderDto.getPrice());
            }

            if (orderDto.getDistance() != null) {
                order.setDistance(orderDto.getDistance());
            }

            if (orderDto.getIsDisable() != null) {
                order.setIsDisable(orderDto.getIsDisable());
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



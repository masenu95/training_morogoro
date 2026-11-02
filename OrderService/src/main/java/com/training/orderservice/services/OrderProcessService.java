package com.training.orderservice.services;

import com.training.orderservice.dto.OrderMessage;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.repository.OrderItemRepository;
import com.training.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderProcessService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessService.class);

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    public boolean sendOrder(OrderMessage message) {
        try {
            boolean sent = streamBridge.send("sendOrder-out-0", message);
            log.info("Order request sent={} {}", sent, message);
            return sent;
        } catch (Exception e) {
            log.error("Order request not sent {} reason={}", message, e.getMessage());
            return false;
        }
    }

    @Transactional
    public Order saveOrder(OrderMessage message) {
        double totalPrice = message.price() * message.quantity();

        Order order = new Order();
        order.setQuantity(message.quantity());
        order.setTotalPrice(totalPrice);
        order.setStatus("PLACED");
        order.setOrderDate(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(message.productId());
        item.setQuantity(message.quantity());
        item.setPrice(message.price());
        item.setTotalAmount(totalPrice);
        orderItemRepository.save(item);

        log.info("Order saved id={} {}", order.getId(), message);
        return order;
    }
}

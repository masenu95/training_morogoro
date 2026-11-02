package com.training.orderservice.config;

import com.training.orderservice.dto.OrderMessage;
import com.training.orderservice.services.OrderProcessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class OrderConsumer {

    @Bean
    public Consumer<OrderMessage> receiveOrders(OrderProcessService orderProcessService) {
        return orderProcessService::saveOrder;
    }
}

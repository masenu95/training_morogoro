package com.mukhusin.myconsumerservice.consumer;

import com.mukhusin.myconsumerservice.dto.OrderMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Configuration
public class MyConsumer {

    List<String> data;

    @Bean
    public Consumer<List<String>> receiveFruits() {
        return fruits -> System.out.println("received fruits:- " + fruits);
    }

    @Bean
    public Consumer<List<String>> orders() {
        System.out.println("received orders:- ");
//        return fruits -> System.out.println("received orders:- " + fruits);
        return (value) -> {
            value.forEach(System.out::println);
            System.out.println("received orders:- " + value);
        };
    }


    @Bean
    public Consumer<OrderMessage> receiveOrder() {
        return order -> System.out.println("received order:- " + order);
    }


}

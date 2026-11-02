package com.mukhusin.myproducer.controller;

import com.mukhusin.myproducer.dto.OrderMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class MyController {

    //case 2
    @Autowired
    StreamBridge streamBridge;

    @GetMapping("/send")
    public String send() {
        List<String> fruits = Arrays.asList("Apple", "Banana", "Pear", "Strawberry");
        boolean sent = streamBridge.send("fruitsSupplier-out-0",
                MessageBuilder.withPayload(fruits).build());
        System.out.println("sent:- " + sent);

        return "sent";
    }

    @PostMapping("/orders")
    public String sendOrder(@RequestBody OrderMessage order) {
        boolean sent = streamBridge.send("orderSupplier-out-0", order);
        System.out.println("order sent:- " + sent + " " + order);
        return sent ? "order sent" : "order not sent";
    }
}

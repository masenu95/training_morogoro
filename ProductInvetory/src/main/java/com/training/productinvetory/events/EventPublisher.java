package com.training.productinvetory.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    @Autowired
    private StreamBridge streamBridge;

    public void publish(String binding, Object event) {
        try {
            boolean sent = streamBridge.send(binding, event);
            log.info("Event published to {} sent={} {}", binding, sent, event);
        } catch (Exception e) {
            log.error("Event not published to {} {} reason={}", binding, event, e.getMessage());
        }
    }
}

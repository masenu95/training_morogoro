package com.training.productinvetory.events;

import com.training.productinvetory.dto.OrderEvent;
import com.training.productinvetory.dto.ProductEvent;
import com.training.productinvetory.entity.Action;
import com.training.productinvetory.entity.ProductInfo;
import com.training.productinvetory.repository.ProductInfoRepository;
import com.training.productinvetory.services.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    @Bean
    public Consumer<ProductEvent> productEvents(ProductInfoRepository productInfoRepository) {
        return event -> {
            ProductInfo product = new ProductInfo();
            product.setId(event.id());
            product.setName(event.name());
            product.setPrice(event.price());
            productInfoRepository.save(product);
            log.info("Product event received {}", event);
        };
    }

    @Bean
    public Consumer<OrderEvent> orderEvents(InventoryService inventoryService) {
        return event -> {
            log.info("Order event received {}", event);
            if ("PLACED".equals(event.status())) {
                if (inventoryService.getAvailable(event.productId()) < event.quantity()) {
                    log.warn("Not enough stock for order {}", event.orderId());
                    return;
                }
                inventoryService.record(event.productId(), event.quantity(), Action.DEDUCT);
            } else if ("CANCELLED".equals(event.status())) {
                inventoryService.record(event.productId(), event.quantity(), Action.ADD);
            }
        };
    }
}

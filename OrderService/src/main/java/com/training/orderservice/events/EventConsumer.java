package com.training.orderservice.events;

import com.training.orderservice.dto.InventoryEvent;
import com.training.orderservice.dto.ProductEvent;
import com.training.orderservice.entity.ProductInfo;
import com.training.orderservice.entity.StockInfo;
import com.training.orderservice.repository.ProductInfoRepository;
import com.training.orderservice.repository.StockInfoRepository;
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
    public Consumer<InventoryEvent> inventoryEvents(StockInfoRepository stockInfoRepository) {
        return event -> {
            StockInfo stock = new StockInfo();
            stock.setProductId(event.productId());
            stock.setAvailableQuantity(event.closingQuantity());
            stockInfoRepository.save(stock);
            log.info("Inventory event received {}", event);
        };
    }
}

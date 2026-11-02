package com.training.productinvetory.services;

import com.training.productinvetory.dto.InventoryEvent;
import com.training.productinvetory.entity.Action;
import com.training.productinvetory.entity.ProductInventory;
import com.training.productinvetory.events.EventPublisher;
import com.training.productinvetory.repository.ProductInventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InventoryService {

    @Autowired
    private ProductInventoryRepository productInventoryRepository;

    @Autowired
    private EventPublisher eventPublisher;

    public Integer getAvailable(Long productId) {
        return productInventoryRepository.findTopByProductIdOrderByIdDesc(productId)
                .map(ProductInventory::getClosingQuantity)
                .orElse(0);
    }

    public ProductInventory record(Long productId, Integer quantity, Action action) {
        int opening = getAvailable(productId);
        int closing = action == Action.ADD ? opening + quantity : opening - quantity;

        ProductInventory inventory = new ProductInventory();
        inventory.setProductId(productId);
        inventory.setOpeningQuantity(opening);
        inventory.setQuantity(quantity);
        inventory.setClosingQuantity(closing);
        inventory.setAction(action);
        inventory.setActionDate(LocalDate.now());
        inventory = productInventoryRepository.save(inventory);

        eventPublisher.publish("inventoryEvents-out-0", new InventoryEvent(productId, quantity, closing, action.name()));
        return inventory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishStock() {
        productInventoryRepository.findAll().stream()
                .map(ProductInventory::getProductId)
                .distinct()
                .forEach(productId -> eventPublisher.publish("inventoryEvents-out-0", new InventoryEvent(productId, 0, getAvailable(productId), "SYNC")));
    }
}

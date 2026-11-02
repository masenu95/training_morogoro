package com.training.productinvetory.controller;

import com.training.productinvetory.dto.InventoryDetails;
import com.training.productinvetory.dto.InventoryRequest;
import com.training.productinvetory.dto.ProductStock;
import com.training.productinvetory.entity.Action;
import com.training.productinvetory.entity.ProductInventory;
import com.training.productinvetory.feignclient.ProductFeignClient;
import com.training.productinvetory.repository.ProductInventoryRepository;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class ProductInventoryController {

    @Autowired
    private ProductInventoryRepository productInventoryRepository;

    @Autowired
    private ProductFeignClient productFeignClient;

    @GetMapping
    public List<InventoryDetails> getInventory() {
        Map<Long, String> productNames = getProductNames();
        return productInventoryRepository.findAll().stream()
                .map(inventory -> new InventoryDetails(
                        inventory.getId(),
                        inventory.getProductId(),
                        productNames.get(inventory.getProductId()),
                        inventory.getOpeningQuantity(),
                        inventory.getQuantity(),
                        inventory.getClosingQuantity(),
                        inventory.getAction(),
                        inventory.getActionDate()))
                .toList();
    }

    @GetMapping("/products")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "productStockFallback")
    public ResponseEntity<?> getProductStock() {
        return ResponseEntity.ok(productFeignClient.getAllProducts().stream()
                .map(product -> new ProductStock(
                        product.getId(),
                        product.getName(),
                        getAvailable(product.getId())))
                .toList());
    }

    @Observed(name = "product-fallback-execution")
    public ResponseEntity<?> productStockFallback(Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Product service may not be available");
    }

    @PostMapping("/add")
    public ResponseEntity<?> addQuantity(@RequestBody InventoryRequest request) {
        return ResponseEntity.ok(record(request, Action.ADD));
    }

    @PostMapping("/deduct")
    public ResponseEntity<?> deductQuantity(@RequestBody InventoryRequest request) {
        if (getAvailable(request.getProductId()) < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient quantity");
        }
        return ResponseEntity.ok(record(request, Action.DEDUCT));
    }

    @GetMapping("/available/{productId}")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "availableFallback")
    public ResponseEntity<?> availableQuantity(@PathVariable Long productId) {
        return ResponseEntity.ok(getAvailable(productId));
    }

    @Observed(name = "available-inventory-fallback-execution")
    public ResponseEntity<?> availableFallback(Long productId, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Product service may not be available");
    }

    @GetMapping("/{productId}")
    public List<ProductInventory> getHistory(@PathVariable Long productId) {
        return productInventoryRepository.findByProductId(productId);
    }

    @GetMapping("/{productId}/today")
    public List<ProductInventory> getToday(@PathVariable Long productId) {
        return productInventoryRepository.findByProductIdAndActionDate(productId, LocalDate.now());
    }

    private Map<Long, String> getProductNames() {
        Map<Long, String> productNames = new HashMap<>();
        try {
            productFeignClient.getAllProducts().forEach(p -> productNames.put(p.getId(), p.getName()));
        } catch (FeignException e) {
            return productNames;
        }
        return productNames;
    }

    private Integer getAvailable(Long productId) {
        return productInventoryRepository.findTopByProductIdOrderByIdDesc(productId)
                .map(ProductInventory::getClosingQuantity)
                .orElse(0);
    }

    private ProductInventory record(InventoryRequest request, Action action) {
        int opening = getAvailable(request.getProductId());
        int closing = action == Action.ADD ? opening + request.getQuantity() : opening - request.getQuantity();

        ProductInventory inventory = new ProductInventory();
        inventory.setProductId(request.getProductId());
        inventory.setOpeningQuantity(opening);
        inventory.setQuantity(request.getQuantity());
        inventory.setClosingQuantity(closing);
        inventory.setAction(action);
        inventory.setActionDate(LocalDate.now());
        return productInventoryRepository.save(inventory);
    }
}

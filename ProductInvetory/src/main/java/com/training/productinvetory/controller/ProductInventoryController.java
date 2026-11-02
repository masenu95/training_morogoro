package com.training.productinvetory.controller;

import com.training.productinvetory.dto.InventoryDetails;
import com.training.productinvetory.dto.InventoryRequest;
import com.training.productinvetory.dto.ProductStock;
import com.training.productinvetory.entity.Action;
import com.training.productinvetory.entity.ProductInfo;
import com.training.productinvetory.entity.ProductInventory;
import com.training.productinvetory.repository.ProductInfoRepository;
import com.training.productinvetory.repository.ProductInventoryRepository;
import com.training.productinvetory.services.InventoryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
public class ProductInventoryController {

    @Autowired
    private ProductInventoryRepository productInventoryRepository;

    @Autowired
    private ProductInfoRepository productInfoRepository;

    @Autowired
    private InventoryService inventoryService;

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
    public List<ProductStock> getProductStock() {
        return productInfoRepository.findAll().stream()
                .map(product -> new ProductStock(
                        product.getId(),
                        product.getName(),
                        inventoryService.getAvailable(product.getId())))
                .toList();
    }

    @PostMapping("/add")
    public ResponseEntity<?> addQuantity(@RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.record(request.getProductId(), request.getQuantity(), Action.ADD));
    }

    @PostMapping("/deduct")
    public ResponseEntity<?> deductQuantity(@RequestBody InventoryRequest request) {
        if (inventoryService.getAvailable(request.getProductId()) < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient quantity");
        }
        return ResponseEntity.ok(inventoryService.record(request.getProductId(), request.getQuantity(), Action.DEDUCT));
    }

    @GetMapping("/available/{productId}")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "availableFallback")
    public ResponseEntity<?> availableQuantity(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getAvailable(productId));
    }

    @Observed(name = "available-inventory-fallback-execution")
    public ResponseEntity<?> availableFallback(Long productId, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Inventory may not be available");
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
        return productInfoRepository.findAll().stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName));
    }
}

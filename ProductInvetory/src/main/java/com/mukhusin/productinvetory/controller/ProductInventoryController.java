package com.mukhusin.productinvetory.controller;

import com.mukhusin.productinvetory.dto.InventoryDetails;
import com.mukhusin.productinvetory.dto.InventoryRequest;
import com.mukhusin.productinvetory.entity.Action;
import com.mukhusin.productinvetory.entity.ProductInventory;
import com.mukhusin.productinvetory.feignclient.ProductFeignClient;
import com.mukhusin.productinvetory.repository.ProductInventoryRepository;
import feign.FeignException;
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
    public Integer availableQuantity(@PathVariable Long productId) {
        return getAvailable(productId);
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

package com.training.addupdateproduct.controller;

import com.training.addupdateproduct.dto.ProductEvent;
import com.training.addupdateproduct.entity.Product;
import com.training.addupdateproduct.events.EventPublisher;
import com.training.addupdateproduct.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @PostMapping
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        Product saved = productRepository.save(product);
        eventPublisher.publish("productEvents-out-0", new ProductEvent(saved.getId(), saved.getName(), saved.getPrice(), "ADDED"));
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        Product existing = productRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        Product saved = productRepository.save(existing);
        eventPublisher.publish("productEvents-out-0", new ProductEvent(saved.getId(), saved.getName(), saved.getPrice(), "UPDATED"));
        return ResponseEntity.ok(saved);
    }
}

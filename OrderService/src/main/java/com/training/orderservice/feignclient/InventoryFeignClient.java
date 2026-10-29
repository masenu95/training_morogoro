package com.training.orderservice.feignclient;

import com.training.orderservice.dto.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", url = "http://localhost:8089")
public interface InventoryFeignClient {

    @GetMapping("/inventory/available/{productId}")
    Integer getAvailableQuantity(@PathVariable("productId") Long productId);

    @PostMapping("/inventory/deduct")
    void deductQuantity(@RequestBody OrderRequest request);

    @PostMapping("/inventory/add")
    void addQuantity(@RequestBody OrderRequest request);
}

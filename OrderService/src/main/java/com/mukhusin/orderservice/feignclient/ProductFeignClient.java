package com.mukhusin.orderservice.feignclient;

import com.mukhusin.orderservice.dto.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "fetch-product-service", url = "http://localhost:8087")
public interface ProductFeignClient {

    @GetMapping("/products")
    List<Product> getAllProducts();

    @GetMapping("/products/{id}")
    Product getProductById(@PathVariable("id") Long id);
}

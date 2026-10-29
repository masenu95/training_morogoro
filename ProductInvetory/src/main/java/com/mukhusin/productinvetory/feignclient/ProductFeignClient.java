package com.mukhusin.productinvetory.feignclient;

import com.mukhusin.productinvetory.dto.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "fetch-product-service", url = "http://localhost:8087")
public interface ProductFeignClient {

    @GetMapping("/products")
    List<Product> getAllProducts();
}

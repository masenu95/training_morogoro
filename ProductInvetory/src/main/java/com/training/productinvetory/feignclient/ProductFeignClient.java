package com.training.productinvetory.feignclient;

import com.training.productinvetory.dto.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "fetch-product-service")
//@FeignClient(name = "fetch-product-service", url = "http://localhost:7001")
public interface ProductFeignClient {

    @GetMapping("/products")
    List<Product> getAllProducts();
}

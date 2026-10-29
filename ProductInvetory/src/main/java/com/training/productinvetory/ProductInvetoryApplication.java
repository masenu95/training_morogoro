package com.training.productinvetory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com")
@EnableDiscoveryClient
public class ProductInvetoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductInvetoryApplication.class, args);
    }

}

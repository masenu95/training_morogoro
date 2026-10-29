package com.mukhusin.addupdateproduct.config;

import com.mukhusin.addupdateproduct.entity.Product;
import com.mukhusin.addupdateproduct.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductSeeder implements CommandLineRunner {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public void run(String... args) {
        save("Laptop", "Dell Inspiron 15 laptop", 1500000.0);
        save("Smartphone", "Samsung Galaxy A54", 850000.0);
        save("Headphones", "Wireless bluetooth headphones", 120000.0);
        save("Keyboard", "Mechanical keyboard", 95000.0);
        save("Mouse", "Wireless optical mouse", 35000.0);
        save("Monitor", "24 inch LED monitor", 450000.0);
        save("Printer", "HP LaserJet printer", 600000.0);
        save("Flash Disk", "64GB USB flash disk", 25000.0);
        save("Router", "TP-Link WiFi router", 80000.0);
        save("Power Bank", "20000mAh power bank", 60000.0);
        save("Tablet", "Lenovo Tab M10", 550000.0);
        save("Speaker", "Portable bluetooth speaker", 75000.0);
    }

    private void save(String name, String description, Double price) {
        if (productRepository.existsByName(name)) {
            return;
        }
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        productRepository.save(product);
    }
}

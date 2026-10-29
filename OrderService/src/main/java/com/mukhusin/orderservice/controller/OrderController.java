package com.mukhusin.orderservice.controller;

import com.mukhusin.orderservice.dto.OrderDetails;
import com.mukhusin.orderservice.dto.OrderItemDetails;
import com.mukhusin.orderservice.dto.OrderRequest;
import com.mukhusin.orderservice.dto.Product;
import com.mukhusin.orderservice.entity.Order;
import com.mukhusin.orderservice.entity.OrderItem;
import com.mukhusin.orderservice.repository.OrderItemRepository;
import com.mukhusin.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final String PRODUCT_URL = "http://localhost:8087/products/";
    private static final String INVENTORY_URL = "http://localhost:8089/inventory/";

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        Product product;
        try {
            product = restTemplate.getForObject(PRODUCT_URL + request.getProductId(), Product.class);
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        Integer available = restTemplate.getForObject(INVENTORY_URL + "available/" + request.getProductId(), Integer.class);
        if (available == null || available < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient quantity, available: " + available);
        }

        restTemplate.postForObject(INVENTORY_URL + "deduct", request, Object.class);

        double totalPrice = product.getPrice() * request.getQuantity();

        Order order = new Order();
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);
        order.setStatus("PLACED");
        order.setOrderDate(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(request.getProductId());
        item.setQuantity(request.getQuantity());
        item.setPrice(product.getPrice());
        item.setTotalAmount(totalPrice);
        orderItemRepository.save(item);

        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Order already cancelled");
        }

        for (OrderItem item : orderItemRepository.findByOrderId(id)) {
            restTemplate.postForObject(INVENTORY_URL + "add", new OrderRequest(item.getProductId(), item.getQuantity()), Object.class);
        }

        order.setStatus("CANCELLED");
        return ResponseEntity.ok(orderRepository.save(order));
    }

    @GetMapping
    public List<OrderDetails> getOrders() {
        Map<Long, String> productNames = getProductNames();
        return orderRepository.findAll().stream()
                .map(order -> new OrderDetails(
                        order.getId(),
                        order.getQuantity(),
                        order.getTotalPrice(),
                        order.getStatus(),
                        order.getOrderDate(),
                        orderItemRepository.findByOrderId(order.getId()).stream()
                                .map(item -> new OrderItemDetails(
                                        item.getId(),
                                        item.getProductId(),
                                        productNames.get(item.getProductId()),
                                        item.getQuantity(),
                                        item.getPrice(),
                                        item.getTotalAmount()))
                                .toList()))
                .toList();
    }

    @GetMapping("/{id}/items")
    public List<OrderItem> getOrderItems(@PathVariable Long id) {
        return orderItemRepository.findByOrderId(id);
    }

    private Map<Long, String> getProductNames() {
        Map<Long, String> productNames = new HashMap<>();
        try {
            ResponseEntity<Product[]> response = restTemplate.getForEntity("http://localhost:8087/products", Product[].class);
            Arrays.stream(response.getBody()).forEach(p -> productNames.put(p.getId(), p.getName()));
        } catch (RestClientException e) {
            return productNames;
        }
        return productNames;
    }
}

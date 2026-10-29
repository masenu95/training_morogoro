package com.training.orderservice.controller;

import com.training.orderservice.dto.OrderDetails;
import com.training.orderservice.dto.OrderItemDetails;
import com.training.orderservice.dto.OrderRequest;
import com.training.orderservice.dto.Product;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.feignclient.InventoryFeignClient;
import com.training.orderservice.feignclient.ProductFeignClient;
import com.training.orderservice.repository.OrderItemRepository;
import com.training.orderservice.repository.OrderRepository;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private InventoryFeignClient inventoryFeignClient;

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        Product product;
        try {
            product = productFeignClient.getProductById(request.getProductId());
        } catch (FeignException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        Integer available = inventoryFeignClient.getAvailableQuantity(request.getProductId());
        if (available == null || available < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient quantity, available: " + available);
        }

        inventoryFeignClient.deductQuantity(request);

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
            inventoryFeignClient.addQuantity(new OrderRequest(item.getProductId(), item.getQuantity()));
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
            productFeignClient.getAllProducts().forEach(p -> productNames.put(p.getId(), p.getName()));
        } catch (FeignException e) {
            return productNames;
        }
        return productNames;
    }
}

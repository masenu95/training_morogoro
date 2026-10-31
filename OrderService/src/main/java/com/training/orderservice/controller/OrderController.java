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
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.observation.annotation.Observed;
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
    @RateLimiter(name = "orderRateLimiter", fallbackMethod = "placeOrderFallback")
    @Bulkhead(name = "orderBulkhead", fallbackMethod = "placeOrderFallback", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "orderService", fallbackMethod = "placeOrderFallback")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        Product product;
        product = productFeignClient.getProductById(request.getProductId());

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

    // FIXED: Added Throwable parameter and removed @RequestBody
    @Observed(name = "place.order-fallback.execution")
    public ResponseEntity<?> placeOrderFallback(OrderRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Product or Inventory service unavailable. Reason: " + t.getMessage());
    }

    @PutMapping("/{id}/cancel")
    @CircuitBreaker(name = "orderService", fallbackMethod = "cancelOrderFallback")
    @Retry(name = "orderRetry", fallbackMethod = "cancelOrderFallback")
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

    // FIXED: Added Throwable parameter, changed parameter to match 'Long id', and removed @PathVariable
    @Observed(name = "place.order-fallback.execution")
    public ResponseEntity<?> cancelOrderFallback(Long id, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Unable to cancel order right now. Service unavailable.");
    }

    @GetMapping
//    @CircuitBreaker(name = "")
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
    public List<OrderItemDetails> getOrderItems(@PathVariable Long id) {
        Map<Long, String> productNames = getProductNames();

        return orderItemRepository.findByOrderId(id).stream()
                .map(item -> new OrderItemDetails(
                        item.getId(),
                        item.getProductId(),
                        productNames.get(item.getProductId()),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getTotalAmount()))
                .toList();
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

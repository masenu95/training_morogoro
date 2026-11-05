package com.training.orderservice.controller;

import com.training.orderservice.dto.OrderDetails;
import com.training.orderservice.dto.OrderItemDetails;
import com.training.orderservice.dto.OrderMessage;
import com.training.orderservice.dto.OrderRequest;
import com.training.orderservice.dto.Product;
import com.training.orderservice.dto.OrderEvent;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.entity.ProductInfo;
import com.training.orderservice.entity.StockInfo;
import com.training.orderservice.events.EventPublisher;
import com.training.orderservice.feignclient.InventoryFeignClient;
import com.training.orderservice.feignclient.ProductFeignClient;
import com.training.orderservice.repository.OrderItemRepository;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.repository.ProductInfoRepository;
import com.training.orderservice.repository.StockInfoRepository;
import com.training.orderservice.services.OrderProcessService;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductInfoRepository productInfoRepository;

    @Autowired
    private StockInfoRepository stockInfoRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private InventoryFeignClient inventoryFeignClient;

    @Autowired
    private OrderProcessService orderProcessService;

    @PostMapping
    @RateLimiter(name = "orderRateLimiter")
    @Bulkhead(name = "orderBulkhead", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "orderService", fallbackMethod = "placeOrderFallback")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        ProductInfo product = productInfoRepository.findById(request.getProductId())
                .orElseGet(() -> fetchProduct(request.getProductId()));
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }

        StockInfo stock = stockInfoRepository.findById(request.getProductId()).orElse(null);
        if (stock == null || stock.getAvailableQuantity() < request.getQuantity()) {
            stock = fetchStock(request.getProductId());
        }
        Integer available = stock.getAvailableQuantity();
        if (available < request.getQuantity()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient quantity, available: " + available);
        }

        if (!orderProcessService.sendOrder(new OrderMessage(request.getProductId(), request.getQuantity(), product.getPrice()))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Order not received, please try again");
        }

        stock.setAvailableQuantity(available - request.getQuantity());
        stockInfoRepository.save(stock);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Order received and will be saved shortly");
    }

    // FIXED: Added Throwable parameter and removed @RequestBody
    @Observed(name = "place.order-fallback.execution")
    public ResponseEntity<?> placeOrderFallback(OrderRequest request, Throwable t) {
        Throwable reason = t instanceof NoFallbackAvailableException && t.getCause() != null ? t.getCause() : t;
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Product or Inventory service unavailable. Reason: " + reason.getMessage());
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

        order.setStatus("CANCELLED");
        order = orderRepository.save(order);

        for (OrderItem item : orderItemRepository.findByOrderId(id)) {
            eventPublisher.publish("orderEvents-out-0", new OrderEvent(order.getId(), item.getProductId(), item.getQuantity(), item.getTotalAmount(), order.getStatus()));
        }

        return ResponseEntity.ok(order);
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

    private ProductInfo fetchProduct(Long productId) {
        try {
            Product p = productFeignClient.getProductById(productId);
            ProductInfo product = new ProductInfo();
            product.setId(p.getId());
            product.setName(p.getName());
            product.setPrice(p.getPrice());
            return productInfoRepository.save(product);
        } catch (FeignException.NotFound e) {
            return null;
        } catch (NoFallbackAvailableException e) {
            if (e.getCause() instanceof FeignException.NotFound) {
                return null;
            }
            throw e;
        }
    }

    private StockInfo fetchStock(Long productId) {
        StockInfo stock = new StockInfo();
        stock.setProductId(productId);
        stock.setAvailableQuantity(inventoryFeignClient.getAvailableQuantity(productId));
        return stockInfoRepository.save(stock);
    }

    private Map<Long, String> getProductNames() {
        return productInfoRepository.findAll().stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName));
    }
}

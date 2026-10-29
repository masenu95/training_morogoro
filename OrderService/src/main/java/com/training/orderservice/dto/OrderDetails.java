package com.training.orderservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetails(Long id, Integer quantity, Double totalPrice, String status, LocalDateTime orderDate, List<OrderItemDetails> orderItems) {
}

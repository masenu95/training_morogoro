package com.training.orderservice.dto;

public record OrderItemDetails(Long id, Long productId, String productName, Integer quantity, Double price, Double totalAmount) {
}

package com.mukhusin.orderservice.dto;

public record OrderItemDetails(Long id, Long productId, String productName, Integer quantity, Double price, Double totalAmount) {
}

package com.training.productinvetory.dto;

public record OrderEvent(Long orderId, Long productId, Integer quantity, Double totalPrice, String status) {
}

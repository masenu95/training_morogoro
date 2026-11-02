package com.training.orderservice.dto;

public record OrderMessage(Long productId, Integer quantity, Double price) {
}

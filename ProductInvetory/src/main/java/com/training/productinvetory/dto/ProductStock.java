package com.training.productinvetory.dto;

public record ProductStock(Long productId, String productName, Integer availableQuantity) {
}

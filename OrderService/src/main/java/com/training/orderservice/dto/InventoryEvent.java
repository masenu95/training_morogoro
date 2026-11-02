package com.training.orderservice.dto;

public record InventoryEvent(Long productId, Integer quantity, Integer closingQuantity, String action) {
}

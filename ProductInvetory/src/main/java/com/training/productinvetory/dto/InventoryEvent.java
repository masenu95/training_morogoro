package com.training.productinvetory.dto;

public record InventoryEvent(Long productId, Integer quantity, Integer closingQuantity, String action) {
}

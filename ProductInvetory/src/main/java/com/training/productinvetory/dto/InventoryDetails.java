package com.training.productinvetory.dto;

import com.training.productinvetory.entity.Action;

import java.time.LocalDate;

public record InventoryDetails(Long id, Long productId, String productName, Integer openingQuantity, Integer quantity, Integer closingQuantity, Action action, LocalDate actionDate) {
}

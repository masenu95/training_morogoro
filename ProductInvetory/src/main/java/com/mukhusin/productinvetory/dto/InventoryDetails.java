package com.mukhusin.productinvetory.dto;

import com.mukhusin.productinvetory.entity.Action;

import java.time.LocalDate;

public record InventoryDetails(Long id, Long productId, String productName, Integer openingQuantity, Integer quantity, Integer closingQuantity, Action action, LocalDate actionDate) {
}

package com.training.productinvetory.dto;

public record ProductEvent(Long id, String name, Double price, String action) {
}

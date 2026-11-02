package com.training.orderservice.dto;

public record ProductEvent(Long id, String name, Double price, String action) {
}

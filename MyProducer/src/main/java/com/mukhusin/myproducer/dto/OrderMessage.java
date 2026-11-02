package com.mukhusin.myproducer.dto;

public record OrderMessage(Long orderId, Long productId, Integer quantity, String status) {
}

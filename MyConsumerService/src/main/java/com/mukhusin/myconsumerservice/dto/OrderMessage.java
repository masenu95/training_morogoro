package com.mukhusin.myconsumerservice.dto;

public record OrderMessage(Long orderId, Long productId, Integer quantity, String status) {
}

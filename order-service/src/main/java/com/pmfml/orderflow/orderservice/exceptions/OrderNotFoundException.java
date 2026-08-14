package com.pmfml.orderflow.orderservice.exceptions;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when an order cannot be found for the given ID and tenant scope.
 */
@Getter
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }
}

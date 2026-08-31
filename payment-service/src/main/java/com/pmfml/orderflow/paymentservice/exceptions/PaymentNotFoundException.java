package com.pmfml.orderflow.paymentservice.exceptions;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    
    private final UUID orderId;

    public PaymentNotFoundException(UUID orderId) {
        super("Payment not found for order: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}

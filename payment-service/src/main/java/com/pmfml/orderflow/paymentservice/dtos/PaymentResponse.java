package com.pmfml.orderflow.paymentservice.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String status,
        String stripePaymentIntentId,
        Instant createdAt,
        Instant updatedAt
) {
}

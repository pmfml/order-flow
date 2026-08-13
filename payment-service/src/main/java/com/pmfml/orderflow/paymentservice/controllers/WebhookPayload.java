package com.pmfml.orderflow.paymentservice.controllers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO representing the payload forwarded by the AWS Lambda webhook function.
 *
 * <p>The Lambda verifies the Stripe signature and normalizes the raw webhook
 * into this simplified structure before forwarding it to the internal endpoint.
 */
@Data
public class WebhookPayload {

    @NotBlank
    private String eventType;

    private String externalReference;

    @NotBlank
    private String orderId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String status;

    @NotNull
    private BigDecimal amount;
}

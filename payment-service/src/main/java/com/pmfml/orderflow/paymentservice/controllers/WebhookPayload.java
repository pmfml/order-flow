package com.pmfml.orderflow.paymentservice.controllers;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WebhookPayload {
    private String eventType;
    private String externalReference;
    private String orderId;
    private String tenantId;
    private String status;
    private BigDecimal amount;
}

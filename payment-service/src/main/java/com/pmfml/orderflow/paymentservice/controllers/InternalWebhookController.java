package com.pmfml.orderflow.paymentservice.controllers;

import com.pmfml.orderflow.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal/v1/payment-webhook")
@RequiredArgsConstructor
public class InternalWebhookController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestBody WebhookPayload payload) {

        log.info("[Payment] Received internal webhook for order {}", payload.getOrderId());

        // Hardcoded check for demonstration. In production, read from application.properties
        String expectedApiKey = System.getenv().getOrDefault("INTERNAL_API_KEY", "dev-secret-key");
        
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            log.warn("[Payment] Unauthorized webhook attempt.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            paymentService.handleWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("[Payment] Failed to process webhook for order {}", payload.getOrderId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

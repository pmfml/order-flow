package com.pmfml.orderflow.paymentservice.controllers;

import com.pmfml.orderflow.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint receiving verified payment webhook payloads from the
 * AWS Lambda function.
 *
 * <p>This endpoint is <strong>not</strong> exposed through the API Gateway
 * (it is on the {@code /internal/*} path, which the Gateway does not route).
 * Authentication is handled via a shared secret in the {@code X-Internal-Api-Key}
 * header, validated against the {@code app.internal-api-key} property.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/payment-webhook")
@RequiredArgsConstructor
public class InternalWebhookController {

    private final PaymentService paymentService;

    @Value("${app.internal-api-key:dev-secret-key}")
    private String expectedApiKey;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody WebhookPayload payload) {

        log.info("[Payment] Received internal webhook for order {}", payload.getOrderId());

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

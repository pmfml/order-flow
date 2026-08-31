package com.pmfml.orderflow.paymentservice.controllers;

import com.pmfml.orderflow.paymentservice.dtos.PaymentResponse;
import com.pmfml.orderflow.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @PathVariable UUID orderId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
            
        log.info("[REST] Received request to fetch payment status for order {} in tenant {}", orderId, tenantId);
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId, tenantId);
        return ResponseEntity.ok(response);
    }
}

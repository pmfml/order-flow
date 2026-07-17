package com.pmfml.orderflow.orderservice.controllers;

import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a new order.
     * <p>Notice that tenant isolation is enforced right from the entry point
     * via the trusted X-Tenant-Id header.
     *
     * @param tenantId the tenant ID forwarded from the gateway
     * @param request  the order payload
     * @return the saved order snapshot
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("[REST] Received request to create order for tenantId: {}", tenantId);

        OrderResponse response = orderService.createOrder(request, tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

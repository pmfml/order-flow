package com.pmfml.orderflow.orderservice.services;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OrderItem;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.grpc.InventoryClient;
import com.pmfml.orderflow.orderservice.repositories.OrderRepository;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Core business logic for Order lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryClient inventoryClient;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    /**
     * Creates an order and its associated outbox event within a single transaction.
     *
     * <p>Prices are securely fetched from the Inventory service, preventing clients
     * from tampering with item values.
     *
     * @param request  the order creation request (items and quantities)
     * @param tenantId the identifier of the tenant context
     * @return the created order response
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String tenantId) {
        log.info("[OrderCreation] Starting order creation: tenantId={}, itemsCount={}", tenantId, request.items().size());

        Order order = Order.builder()
                .tenantId(tenantId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (var itemRequest : request.items()) {
            // 1. Fetch authoritative product info (avoids trust in client prices)
            InventoryClient.ProductInfo productInfo = inventoryClient.fetchProduct(itemRequest.productId());

            // 2. Build the snapshot line item
            OrderItem orderItem = OrderItem.builder()
                    .productId(productInfo.productId())
                    .productName(productInfo.name())
                    .quantity(itemRequest.quantity())
                    .unitPrice(productInfo.price())
                    .build();

            order.addItem(orderItem);

            // 3. Accumulate total
            BigDecimal itemTotal = productInfo.price().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);

        // 4. Save the order aggregate
        Order savedOrder = orderRepository.save(order);

        // 5. Materialize the domain event in the outbox
        writeOutboxEvent(savedOrder);

        log.info("[OrderCreation] Order created successfully: id={}, tenantId={}, totalAmount={}",
                savedOrder.getId(), tenantId, savedOrder.getTotalAmount());

        return orderMapper.toResponse(savedOrder);
    }

    private void writeOutboxEvent(Order order) {
        try {
            // Minimal event payload payload for Saga choreography.
            // Other services only need to know this order exists and belongs to a tenant.
            String payload = objectMapper.writeValueAsString(Map.of(
                    "orderId", order.getId().toString(),
                    "tenantId", order.getTenantId(),
                    "status", order.getStatus().name(),
                    "totalAmount", order.getTotalAmount()
            ));

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .eventType("orders.created")
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("[Outbox] Wrote orders.created event: orderId={}", order.getId());
            
        } catch (JacksonException e) {
            log.error("[Outbox] Failed to serialize order event payload: orderId={}", order.getId(), e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}

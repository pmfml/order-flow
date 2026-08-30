package com.pmfml.orderflow.orderservice.services;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.exceptions.OrderNotFoundException;
import com.pmfml.orderflow.orderservice.exceptions.OutboxSerializationException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            // 1. Fetch authoritative product info and assert the requested quantity
            //    is available (avoids trust in client prices and oversells)
            InventoryClient.ProductInfo productInfo = inventoryClient.fetchAvailableProduct(
                    itemRequest.productId(), itemRequest.quantity(), tenantId);

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

    /**
     * Lists all orders belonging to a tenant, most recent first.
     *
     * @param tenantId the tenant identifier
     * @return ordered list of order responses
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTenant(String tenantId) {
        log.info("[OrderQuery] Listing orders: tenantId={}", tenantId);
        return orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    /**
     * Fetches a single order by ID, scoped to the authenticated tenant.
     *
     * @param orderId  the order UUID
     * @param tenantId the tenant identifier
     * @return the order response
     * @throws OrderNotFoundException if no order matches the ID within the tenant
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, String tenantId) {
        log.info("[OrderQuery] Fetching order: orderId={}, tenantId={}", orderId, tenantId);
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Cancels an order manually (user-initiated, not Saga-driven).
     *
     * <p>Only {@link OrderStatus#PENDING} orders can be cancelled. If the order
     * is already {@code CONFIRMED} or {@code CANCELLED}, the entity's state machine
     * throws {@link IllegalStateException}, which the controller surfaces as 409 Conflict.
     *
     * <p>A {@code orders.cancelled} outbox event is written in the same transaction
     * so the Inventory Service can release any held stock reservation.
     *
     * @param orderId  the order UUID
     * @param tenantId the tenant identifier
     * @return the updated order response
     * @throws OrderNotFoundException if no order matches the ID within the tenant
     * @throws IllegalStateException  if the order is not in a cancellable state
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String tenantId) {
        log.info("[OrderCancel] Cancelling order: orderId={}, tenantId={}", orderId, tenantId);

        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.cancel();
        orderRepository.save(order);

        writeCancellationOutboxEvent(order);

        log.info("[OrderCancel] Order cancelled successfully: orderId={}, tenantId={}",
                orderId, tenantId);

        return orderMapper.toResponse(order);
    }

    /**
     * Materializes an {@code orders.created} event in the outbox table.
     *
     * <p>Only the event-specific data is stored here. The envelope fields defined
     * in §7.2 ({@code eventId}, {@code eventType}, {@code tenantId},
     * {@code occurredAt}) are derived from this row's own columns when the poller
     * publishes it, keeping the row the single source of truth.
     *
     * <p>Line items are part of the payload because the Inventory Service needs
     * {@code productId} and {@code quantity} to reserve stock without calling
     * back into this service.
     */
    private void writeOutboxEvent(Order order) {
        try {
            List<Map<String, Object>> items = order.getItems().stream()
                    .map(item -> {
                        Map<String, Object> line = new LinkedHashMap<>();
                        line.put("productId", item.getProductId());
                        line.put("quantity", item.getQuantity());
                        line.put("unitPrice", item.getUnitPrice());
                        return line;
                    })
                    .toList();

            // LinkedHashMap keeps the serialized field order aligned with docs/EVENTS.md.
            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("orderId", order.getId().toString());
            payloadData.put("status", order.getStatus().name());
            payloadData.put("totalAmount", order.getTotalAmount());
            payloadData.put("items", items);

            String payload = objectMapper.writeValueAsString(payloadData);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .tenantId(order.getTenantId())
                    .eventType(EventTypes.ORDER_CREATED)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("[Outbox] Wrote {} event: orderId={}", EventTypes.ORDER_CREATED, order.getId());
            
        } catch (JacksonException e) {
            throw new OutboxSerializationException(
                    "Failed to serialize outbox event payload for order %s".formatted(order.getId()), e);
        }
    }

    /**
     * Writes an {@code orders.cancelled} event to the outbox for manual cancellations.
     *
     * <p>The payload mirrors the structure used by {@link SagaReactionService}
     * for Saga-driven cancellations, keeping downstream consumers consistent.
     */
    private void writeCancellationOutboxEvent(Order order) {
        try {
            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("orderId", order.getId().toString());
            payloadData.put("status", order.getStatus().name());

            String payload = objectMapper.writeValueAsString(payloadData);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .tenantId(order.getTenantId())
                    .eventType(EventTypes.ORDER_CANCELLED)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("[Outbox] Wrote {} event: orderId={}", EventTypes.ORDER_CANCELLED, order.getId());

        } catch (JacksonException e) {
            throw new OutboxSerializationException(
                    "Failed to serialize cancellation outbox event for order %s".formatted(order.getId()), e);
        }
    }
}

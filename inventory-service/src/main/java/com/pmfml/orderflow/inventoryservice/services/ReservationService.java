package com.pmfml.orderflow.inventoryservice.services;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.inventoryservice.entities.ProcessedEvent;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.entities.ReservationStatus;
import com.pmfml.orderflow.inventoryservice.entities.StockReservation;
import com.pmfml.orderflow.inventoryservice.repositories.ProcessedEventRepository;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import com.pmfml.orderflow.inventoryservice.repositories.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that handles stock reservations upon order creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public void handleOrderCreated(EventEnvelope event) {
        // 1. Idempotency Check: deduplicate against processed_events collection
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .id(event.eventId().toString())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        try {
            processedEventRepository.insert(processedEvent);
        } catch (DuplicateKeyException e) {
            log.info("[Reservation] Event {} already processed, skipping.", event.eventId());
            return;
        }

        log.info("[Reservation] Processing order created event for orderId: {}", event.payload().get("orderId"));

        boolean success = false;
        try {
            success = attemptReservation(event);
        } catch (Exception e) {
            log.error("[Reservation] Unexpected error during reservation for event {}: {}", event.eventId(), e.getMessage());
            // We re-throw so the Spring Kafka listener container can catch it and retry (e.g. for OptimisticLockingFailureException).
            // If it keeps failing, it will be routed to the DLT.
            throw e;
        }

        // Publish appropriate outcome event
        String outcomeEventType = success ? EventTypes.INVENTORY_RESERVED : EventTypes.INVENTORY_RESERVATION_FAILED;
        publishOutcome(event, outcomeEventType);
    }

    private boolean attemptReservation(EventEnvelope event) {
        String orderId = (String) event.payload().get("orderId");
        String tenantId = event.tenantId();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) event.payload().get("items");

        List<StockReservation> createdReservations = new ArrayList<>();
        List<Product> productsToSave = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String productId = (String) item.get("productId");
            int quantity = (Integer) item.get("quantity");

            Product product = productRepository.findByIdAndTenantId(productId, tenantId).orElse(null);

            if (product == null) {
                log.warn("[Reservation] Product {} not found for tenant {}", productId, tenantId);
                rollback(createdReservations, productsToSave);
                return false;
            }

            int onHand = product.getStockQuantity() == null ? 0 : product.getStockQuantity();

            if (onHand < quantity) {
                log.warn("[Reservation] Insufficient stock for product {}: requested {}, available {}", productId, quantity, onHand);
                rollback(createdReservations, productsToSave);
                return false;
            }

            // Deduct stock
            product.setStockQuantity(onHand - quantity);
            productsToSave.add(product);

            // Create reservation
            StockReservation reservation = StockReservation.builder()
                    .orderId(orderId)
                    .tenantId(tenantId)
                    .productId(productId)
                    .quantity(quantity)
                    .status(ReservationStatus.RESERVED)
                    .createdAt(Instant.now())
                    // TTL: Automatically expire in 15 minutes if no confirmation/cancellation arrives
                    .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                    .build();
            createdReservations.add(reservation);
        }

        // Save all changes. Optimistic locking on Product prevents lost updates.
        // If an OptimisticLockingFailureException occurs, it will bubble up, trigger a Kafka retry, 
        // and re-evaluate the stock.
        productRepository.saveAll(productsToSave);
        stockReservationRepository.saveAll(createdReservations);
        
        log.info("[Reservation] Successfully reserved stock for order {}", orderId);
        return true;
    }

    private void rollback(List<StockReservation> createdReservations, List<Product> productsToSave) {
        // Since we evaluate completely in-memory before saving, if we hit a failure,
        // we simply don't save the modified products and reservations. 
        // No DB rollback is needed.
        log.info("[Reservation] Reservation failed. No changes were committed to DB.");
    }

    private void publishOutcome(EventEnvelope triggerEvent, String outcomeEventType) {
        String orderIdStr = (String) triggerEvent.payload().get("orderId");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderIdStr);

        // Forward totalAmount so the Payment Service knows how much to authorize.
        // Only relevant for inventory.reserved; harmless if present on failure events.
        Object totalAmount = triggerEvent.payload().get("totalAmount");
        if (totalAmount != null) {
            payload.put("totalAmount", totalAmount);
        }

        EventEnvelope outcomeEvent = new EventEnvelope(
                UUID.randomUUID(), // New distinct event ID for the outcome
                outcomeEventType,
                triggerEvent.tenantId(),
                Instant.now(),
                payload
        );

        try {
            String message = objectMapper.writeValueAsString(outcomeEvent);
            // OrderId acts as the partition key to preserve ordering per saga
            kafkaTemplate.send(outcomeEventType, orderIdStr, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Reservation] Failed to publish outcome event {}", outcomeEventType, ex);
                        } else {
                            log.debug("[Reservation] Published outcome event {}", outcomeEventType);
                        }
                    });
        } catch (Exception e) {
            log.error("[Reservation] Failed to serialize outcome event {}", outcomeEventType, e);
            throw new RuntimeException(e);
        }
    }

    public void handleOrderCancelled(EventEnvelope event) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .id(event.eventId().toString())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        try {
            processedEventRepository.insert(processedEvent);
        } catch (DuplicateKeyException e) {
            log.info("[Reservation] Cancel event {} already processed, skipping.", event.eventId());
            return;
        }

        String orderId = (String) event.payload().get("orderId");
        String tenantId = event.tenantId();
        
        log.info("[Reservation] Processing cancellation for order: {}", orderId);

        List<StockReservation> reservations = stockReservationRepository.findByOrderIdAndTenantId(orderId, tenantId);
        
        if (reservations.isEmpty()) {
            log.warn("[Reservation] No reservations found for order {}", orderId);
            return;
        }

        List<Product> productsToSave = new ArrayList<>();
        List<StockReservation> reservationsToSave = new ArrayList<>();

        for (StockReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                continue; // Already released
            }

            Product product = productRepository.findByIdAndTenantId(reservation.getProductId(), tenantId).orElse(null);
            if (product != null) {
                // Return stock
                int onHand = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
                product.setStockQuantity(onHand + reservation.getQuantity());
                productsToSave.add(product);
            } else {
                log.warn("[Reservation] Product {} not found when trying to release stock for order {}", reservation.getProductId(), orderId);
            }

            reservation.setStatus(ReservationStatus.RELEASED);
            reservationsToSave.add(reservation);
        }

        productRepository.saveAll(productsToSave);
        stockReservationRepository.saveAll(reservationsToSave);
        
        log.info("[Reservation] Successfully released stock for cancelled order {}", orderId);
    }
}

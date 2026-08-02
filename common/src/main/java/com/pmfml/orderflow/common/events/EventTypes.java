package com.pmfml.orderflow.common.events;

/**
 * The complete set of domain event names exchanged over Kafka, as defined in
 * {@code docs/ARCHITECTURE.md} §7.1.
 *
 * <p>Each constant doubles as the Kafka <strong>topic name</strong>: the naming
 * convention {@code <domain>.<event-in-past-tense>} is shared by both, so the
 * Outbox poller routes an event using its own {@code eventType} and needs no
 * separate topic mapping.
 *
 * <p><strong>Why String constants instead of an enum:</strong> STEERING §12 asks
 * for enums when grouping related constants, but {@code @KafkaListener(topics =
 * ...)} requires a compile-time constant expression, which an enum reference
 * cannot satisfy. String constants keep the values usable from annotations while
 * still centralizing them in one place.
 */
public final class EventTypes {

    // --- Produced by Order Service ---
    public static final String ORDER_CREATED = "orders.created";
    public static final String ORDER_CONFIRMED = "orders.confirmed";
    public static final String ORDER_CANCELLED = "orders.cancelled";

    // --- Produced by Inventory Service ---
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation-failed";

    // --- Produced by Payment Service ---
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CAPTURED = "payment.captured";

    private EventTypes() {
        // Constants holder, not instantiable.
    }
}

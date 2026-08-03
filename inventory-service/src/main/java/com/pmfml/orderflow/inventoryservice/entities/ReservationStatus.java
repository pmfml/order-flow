package com.pmfml.orderflow.inventoryservice.entities;

/**
 * Lifecycle status of a stock reservation.
 *
 * <ul>
 *     <li>{@code RESERVED}: The initial state when an order is created and stock is successfully held.</li>
 *     <li>{@code CONFIRMED}: The final state after the saga completes successfully (e.g., payment authorized).</li>
 *     <li>{@code RELEASED}: The compensating state if the saga fails (e.g., payment fails or order cancelled) or if the TTL expires.</li>
 * </ul>
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}

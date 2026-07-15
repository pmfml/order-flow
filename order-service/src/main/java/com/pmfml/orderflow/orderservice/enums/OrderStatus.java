package com.pmfml.orderflow.orderservice.enums;

/**
 * Represents the lifecycle states of an Order within the choreographed Saga.
 *
 * <p>State transitions:
 * <ul>
 *     <li>{@code PENDING} → {@code CONFIRMED} (all Saga steps succeeded)</li>
 *     <li>{@code PENDING} → {@code CANCELLED} (any Saga step failed, compensation applied)</li>
 * </ul>
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}

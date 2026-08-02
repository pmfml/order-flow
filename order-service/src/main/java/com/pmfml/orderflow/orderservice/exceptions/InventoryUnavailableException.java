package com.pmfml.orderflow.orderservice.exceptions;

/**
 * Raised when the Inventory Service cannot be reached or fails to answer.
 *
 * <p>Distinct from {@link InsufficientStockException}: nothing is wrong with the
 * order, a dependency is down. Callers may retry, which is why this maps to 503
 * rather than 500.
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

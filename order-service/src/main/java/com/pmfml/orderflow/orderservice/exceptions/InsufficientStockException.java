package com.pmfml.orderflow.orderservice.exceptions;

import lombok.Getter;

/**
 * Raised when the catalog holds fewer units than the order line requests.
 *
 * <p>This is a business outcome, not a server fault: the request was well formed
 * and understood, it simply conflicts with the current state of stock.
 */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String productId, int requestedQuantity, int availableQuantity) {
        super("Insufficient stock for product %s: requested %d, available %d"
                .formatted(productId, requestedQuantity, availableQuantity));
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}

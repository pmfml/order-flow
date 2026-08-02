package com.pmfml.orderflow.orderservice.exceptions;

import lombok.Getter;

/**
 * Raised when a product is unknown to the requesting tenant.
 *
 * <p>The catalog lookup is tenant-scoped, so a product owned by another tenant is
 * indistinguishable from one that does not exist. That is deliberate: reporting
 * "exists, but not yours" would leak the catalog of other tenants.
 */
@Getter
public class ProductNotFoundException extends RuntimeException {

    private final String productId;

    public ProductNotFoundException(String productId, Throwable cause) {
        super("Product %s not found for this tenant".formatted(productId), cause);
        this.productId = productId;
    }
}

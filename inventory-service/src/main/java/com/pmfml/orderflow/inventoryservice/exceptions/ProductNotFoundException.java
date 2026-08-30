package com.pmfml.orderflow.inventoryservice.exceptions;

public class ProductNotFoundException extends RuntimeException {

    private final String productId;

    public ProductNotFoundException(String productId) {
        super("Product not found or doesn't belong to the current tenant.");
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}

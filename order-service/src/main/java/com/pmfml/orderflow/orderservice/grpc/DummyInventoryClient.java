package com.pmfml.orderflow.orderservice.grpc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Temporary dummy implementation of the {@link InventoryClient}.
 *
 * <p>Will be replaced by a real gRPC stub in Phase 2.
 */
@Component
public class DummyInventoryClient implements InventoryClient {

    @Override
    public ProductInfo fetchProduct(String productId) {
        // Dummy data: returns a mock product with a fixed price of 100.00
        return new ProductInfo(productId, "Mock Product " + productId, new BigDecimal("100.00"));
    }
}

package com.pmfml.orderflow.orderservice.grpc;

import java.math.BigDecimal;

/**
 * Client interface to communicate with the Inventory Service.
 *
 * <p>In Phase 2, this will be implemented using a native gRPC client
 * stub. For now, it provides a contract to fetch authoritative product
 * prices and names.
 */
public interface InventoryClient {

    /**
     * Fetches product details required for order creation.
     *
     * @param productId the identifier of the product
     * @return authoritative product details
     */
    ProductInfo fetchProduct(String productId);

    record ProductInfo(String productId, String name, BigDecimal price) {}
}

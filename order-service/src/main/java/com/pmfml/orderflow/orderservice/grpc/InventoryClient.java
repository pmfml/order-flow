package com.pmfml.orderflow.orderservice.grpc;

import java.math.BigDecimal;

/**
 * Client interface to communicate with the Inventory Service.
 *
 * <p>Order creation never trusts client-supplied prices or names: they are read
 * from the catalog through this contract instead.
 */
public interface InventoryClient {

    /**
     * Fetches product details for an order line, asserting that the requested
     * quantity can be served.
     *
     * <p>The quantity is part of the question, not an afterthought: asking only
     * whether a product exists would let an order for 500 units through when a
     * single unit is in stock.
     *
     * @param productId the identifier of the product
     * @param quantity  units being ordered; must be greater than zero
     * @param tenantId  the identifier of the tenant
     * @return authoritative product details
     * @throws RuntimeException if the product is unknown to the tenant or has
     *                          insufficient stock
     */
    ProductInfo fetchAvailableProduct(String productId, int quantity, String tenantId);

    record ProductInfo(String productId, String name, BigDecimal price) {}
}

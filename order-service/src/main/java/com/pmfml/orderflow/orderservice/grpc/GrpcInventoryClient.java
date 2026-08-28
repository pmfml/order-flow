package com.pmfml.orderflow.orderservice.grpc;

import com.pmfml.orderflow.common.grpc.inventory.CheckStockRequest;
import com.pmfml.orderflow.common.grpc.inventory.CheckStockResponse;
import com.pmfml.orderflow.common.grpc.inventory.InventoryServiceGrpc;
import com.pmfml.orderflow.orderservice.exceptions.InsufficientStockException;
import com.pmfml.orderflow.orderservice.exceptions.InventoryUnavailableException;
import com.pmfml.orderflow.orderservice.exceptions.ProductNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Real implementation of the {@link InventoryClient} using gRPC.
 */
@Slf4j
@Service
public class GrpcInventoryClient implements InventoryClient {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public GrpcInventoryClient(InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub) {
        this.inventoryStub = inventoryServiceStub;
    }

    @Override
    public ProductInfo fetchAvailableProduct(String productId, int quantity, String tenantId) {
        log.debug("[gRPC Client] Calling CheckStock for product '{}', tenant '{}', quantity {}",
                productId, quantity, tenantId);

        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setProductId(productId)
                .setTenantId(tenantId)
                .setQuantity(quantity)
                .build();

        CheckStockResponse response;
        try {
            response = inventoryStub.checkStock(request);
        } catch (StatusRuntimeException e) {
            throw translate(e, productId);
        }

        if (!response.getAvailable()) {
            throw new InsufficientStockException(productId, quantity, response.getAvailableQuantity());
        }

        return new ProductInfo(
                response.getProductId(),
                response.getName(),
                new BigDecimal(response.getPrice())
        );
    }

    /**
     * Maps a gRPC status onto a domain exception.
     *
     * <p>Without this every remote failure surfaced identically as an opaque 500,
     * so an unknown product and a downed Inventory Service were indistinguishable
     * to the caller.
     */
    // Package-private so the mapping can be unit tested directly. The stub itself
    // is supplied through constructor injection, allowing easy mocking.
    RuntimeException translate(StatusRuntimeException e, String productId) {
        Status.Code code = e.getStatus().getCode();

        return switch (code) {
            case NOT_FOUND -> new ProductNotFoundException(productId, e);
            case UNAVAILABLE, DEADLINE_EXCEEDED -> new InventoryUnavailableException(
                    "Inventory Service did not answer the stock check for product %s"
                            .formatted(productId), e);
            // INVALID_ARGUMENT and anything else indicate a defect on this side
            // (a malformed request we built), so they stay a server fault.
            default -> new IllegalStateException(
                    "Unexpected gRPC status %s from the stock check for product %s"
                            .formatted(code, productId), e);
        };
    }
}

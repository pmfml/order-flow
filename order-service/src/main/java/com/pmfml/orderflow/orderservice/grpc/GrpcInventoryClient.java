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
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Real implementation of the {@link InventoryClient} using gRPC.
 */
@Slf4j
@Service
public class GrpcInventoryClient implements InventoryClient {

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

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
    // arrives through @GrpcClient field injection, which cannot be supplied from a
    // test without reflection; moving it to constructor injection is a follow-up.
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

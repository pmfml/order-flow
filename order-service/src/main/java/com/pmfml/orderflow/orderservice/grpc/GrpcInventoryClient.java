package com.pmfml.orderflow.orderservice.grpc;

import com.pmfml.orderflow.common.grpc.inventory.CheckStockRequest;
import com.pmfml.orderflow.common.grpc.inventory.CheckStockResponse;
import com.pmfml.orderflow.common.grpc.inventory.InventoryServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Real implementation of the {@link InventoryClient} using gRPC.
 */
@Slf4j
@Service
@Primary
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

        CheckStockResponse response = inventoryStub.checkStock(request);

        if (!response.getAvailable()) {
            throw new RuntimeException(
                    "Insufficient stock for product %s: requested %d, available %d"
                            .formatted(productId, quantity, response.getAvailableQuantity()));
        }

        return new ProductInfo(
                response.getProductId(),
                response.getName(),
                new BigDecimal(response.getPrice())
        );
    }
}

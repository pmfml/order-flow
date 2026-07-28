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
    public ProductInfo fetchProduct(String productId, String tenantId) {
        log.debug("[gRPC Client] Calling CheckStock for product '{}', tenant '{}'", productId, tenantId);

        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setProductId(productId)
                .setTenantId(tenantId)
                .build();

        CheckStockResponse response = inventoryStub.checkStock(request);

        if (!response.getAvailable()) {
            throw new RuntimeException("Product is out of stock or unavailable");
        }

        return new ProductInfo(
                response.getProductId(),
                response.getName(),
                new BigDecimal(response.getPrice())
        );
    }
}

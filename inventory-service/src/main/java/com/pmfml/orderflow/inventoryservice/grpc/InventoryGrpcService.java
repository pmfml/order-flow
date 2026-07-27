package com.pmfml.orderflow.inventoryservice.grpc;

import com.pmfml.orderflow.common.grpc.inventory.CheckStockRequest;
import com.pmfml.orderflow.common.grpc.inventory.CheckStockResponse;
import com.pmfml.orderflow.common.grpc.inventory.InventoryServiceGrpc;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductRepository productRepository;

    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        log.info("[gRPC] Received CheckStock for product '{}', tenant '{}'", request.getProductId(), request.getTenantId());

        Optional<Product> productOpt = productRepository.findByIdAndTenantId(request.getProductId(), request.getTenantId());

        if (productOpt.isEmpty()) {
            log.warn("[gRPC] Product '{}' not found for tenant '{}'", request.getProductId(), request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Product not found or doesn't belong to tenant")
                    .asRuntimeException());
            return;
        }

        Product product = productOpt.get();
        boolean isAvailable = product.getStockQuantity() != null && product.getStockQuantity() > 0;

        CheckStockResponse response = CheckStockResponse.newBuilder()
                .setAvailable(isAvailable)
                .setProductId(product.getId())
                .setName(product.getName())
                .setPrice(product.getPrice().toPlainString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

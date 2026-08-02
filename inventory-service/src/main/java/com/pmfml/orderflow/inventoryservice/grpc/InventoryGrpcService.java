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

    /**
     * Reports whether the requested quantity can be served from stock, along with
     * the authoritative name and price.
     *
     * <p>This is a read-only pre-check, not a reservation: stock is only held when
     * the Inventory Service consumes {@code orders.created}. Between this call and
     * that reservation another order may consume the same units, so a positive
     * answer here is an optimistic signal rather than a guarantee. The Saga's
     * compensation path is what makes that safe.
     */
    @Override
    public void checkStock(CheckStockRequest request, StreamObserver<CheckStockResponse> responseObserver) {
        log.info("[gRPC] Received CheckStock for product '{}', tenant '{}', quantity {}",
                request.getProductId(), request.getTenantId(), request.getQuantity());

        if (request.getQuantity() <= 0) {
            log.warn("[gRPC] Rejected CheckStock for product '{}': quantity {} is not positive",
                    request.getProductId(), request.getQuantity());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("quantity must be greater than zero")
                    .asRuntimeException());
            return;
        }

        Optional<Product> productOpt = productRepository.findByIdAndTenantId(request.getProductId(), request.getTenantId());

        if (productOpt.isEmpty()) {
            log.warn("[gRPC] Product '{}' not found for tenant '{}'", request.getProductId(), request.getTenantId());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Product not found or doesn't belong to tenant")
                    .asRuntimeException());
            return;
        }

        Product product = productOpt.get();
        int onHand = product.getStockQuantity() == null ? 0 : product.getStockQuantity();

        // Compare against the requested amount, not merely against zero.
        boolean isAvailable = onHand >= request.getQuantity();

        if (!isAvailable) {
            log.info("[gRPC] Insufficient stock for product '{}': requested {}, on hand {}",
                    product.getId(), request.getQuantity(), onHand);
        }

        CheckStockResponse response = CheckStockResponse.newBuilder()
                .setAvailable(isAvailable)
                .setProductId(product.getId())
                .setName(product.getName())
                .setPrice(product.getPrice().toPlainString())
                .setAvailableQuantity(onHand)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

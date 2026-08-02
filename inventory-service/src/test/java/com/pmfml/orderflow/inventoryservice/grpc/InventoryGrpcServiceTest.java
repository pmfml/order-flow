package com.pmfml.orderflow.inventoryservice.grpc;

import com.pmfml.orderflow.common.grpc.inventory.CheckStockRequest;
import com.pmfml.orderflow.common.grpc.inventory.CheckStockResponse;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcServiceTest {

    private static final String PRODUCT_ID = "prod-1";
    private static final String TENANT_ID = "tenant-123";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StreamObserver<CheckStockResponse> responseObserver;

    @Captor
    private ArgumentCaptor<CheckStockResponse> responseCaptor;

    @Captor
    private ArgumentCaptor<Throwable> errorCaptor;

    @InjectMocks
    private InventoryGrpcService inventoryGrpcService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(PRODUCT_ID)
                .tenantId(TENANT_ID)
                .sku("SKU-1")
                .name("Laptop")
                .price(new BigDecimal("1500.50"))
                .stockQuantity(10)
                .build();
    }

    @Test
    void shouldReportAvailableWhenStockExceedsRequestedQuantity() {
        // Given
        givenProductExists();

        // When
        inventoryGrpcService.checkStock(request(3), responseObserver);

        // Then
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        CheckStockResponse response = responseCaptor.getValue();
        assertThat(response.getAvailable()).isTrue();
        assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getName()).isEqualTo("Laptop");
        assertThat(response.getAvailableQuantity()).isEqualTo(10);

        // Price travels as a string so BigDecimal precision survives the wire
        assertThat(new BigDecimal(response.getPrice())).isEqualByComparingTo("1500.50");
    }

    @Test
    void shouldReportAvailableWhenRequestedQuantityExactlyMatchesStock() {
        // Given — the boundary that separates "enough" from "one too many"
        givenProductExists();

        // When
        inventoryGrpcService.checkStock(request(10), responseObserver);

        // Then
        verify(responseObserver).onNext(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getAvailable()).isTrue();
    }

    @Test
    void shouldReportUnavailableWhenRequestedQuantityExceedsStock() {
        // Given — stock is positive but not enough. Checking only for "any stock"
        // would wrongly accept this order.
        givenProductExists();

        // When
        inventoryGrpcService.checkStock(request(11), responseObserver);

        // Then
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        CheckStockResponse response = responseCaptor.getValue();
        assertThat(response.getAvailable()).isFalse();

        // Still reports the authoritative figures so the caller can say how short it was
        assertThat(response.getAvailableQuantity()).isEqualTo(10);
        assertThat(response.getName()).isEqualTo("Laptop");
    }

    @Test
    void shouldReportUnavailableWhenStockIsZero() {
        // Given
        product.setStockQuantity(0);
        givenProductExists();

        // When
        inventoryGrpcService.checkStock(request(1), responseObserver);

        // Then
        verify(responseObserver).onNext(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getAvailable()).isFalse();
        assertThat(responseCaptor.getValue().getAvailableQuantity()).isZero();
    }

    @Test
    void shouldTreatNullStockQuantityAsUnavailable() {
        // Given — legacy catalog documents may predate the stock field
        product.setStockQuantity(null);
        givenProductExists();

        // When
        inventoryGrpcService.checkStock(request(1), responseObserver);

        // Then
        verify(responseObserver).onNext(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getAvailable()).isFalse();
        assertThat(responseCaptor.getValue().getAvailableQuantity()).isZero();
    }

    @Test
    void shouldFailWithNotFoundWhenProductDoesNotBelongToTenant() {
        // Given — the repository query is scoped by tenant, so another tenant's
        // product is indistinguishable from a missing one.
        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.empty());

        // When
        inventoryGrpcService.checkStock(request(1), responseObserver);

        // Then
        verify(responseObserver).onError(errorCaptor.capture());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();

        assertThat(errorCaptor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException error = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void shouldFailWithInvalidArgumentWhenQuantityIsNotPositive() {
        // When
        inventoryGrpcService.checkStock(request(0), responseObserver);

        // Then — rejected before touching the catalog
        verify(responseObserver).onError(errorCaptor.capture());
        verify(responseObserver, never()).onNext(any());
        verify(productRepository, never()).findByIdAndTenantId(any(), any());

        StatusRuntimeException error = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void shouldFailWithInvalidArgumentWhenQuantityIsMissingFromTheRequest() {
        // Given — proto3 scalars default to 0 when a caller omits the field, so an
        // older client that never sets quantity must not be treated as asking for one.
        CheckStockRequest requestWithoutQuantity = CheckStockRequest.newBuilder()
                .setProductId(PRODUCT_ID)
                .setTenantId(TENANT_ID)
                .build();

        // When
        inventoryGrpcService.checkStock(requestWithoutQuantity, responseObserver);

        // Then
        verify(responseObserver).onError(errorCaptor.capture());
        verify(productRepository, never()).findByIdAndTenantId(any(), any());

        StatusRuntimeException error = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private void givenProductExists() {
        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.of(product));
    }

    private CheckStockRequest request(int quantity) {
        return CheckStockRequest.newBuilder()
                .setProductId(PRODUCT_ID)
                .setTenantId(TENANT_ID)
                .setQuantity(quantity)
                .build();
    }
}

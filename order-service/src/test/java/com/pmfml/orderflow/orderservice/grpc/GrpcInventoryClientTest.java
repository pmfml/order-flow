package com.pmfml.orderflow.orderservice.grpc;

import com.pmfml.orderflow.orderservice.exceptions.InventoryUnavailableException;
import com.pmfml.orderflow.orderservice.exceptions.ProductNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the translation from gRPC status to domain exception.
 *
 * <p>The mapping is what lets the REST layer answer 404 for an unknown product and
 * 503 for an unreachable Inventory Service. Before it existed every remote failure
 * surfaced identically as an opaque 500.
 */
class GrpcInventoryClientTest {

    private static final String PRODUCT_ID = "prod-1";

    private final GrpcInventoryClient client = new GrpcInventoryClient(null);

    @Test
    void shouldTranslateNotFoundToProductNotFound() {
        RuntimeException translated = client.translate(statusException(Status.NOT_FOUND), PRODUCT_ID);

        assertThat(translated)
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID);
        assertThat(((ProductNotFoundException) translated).getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void shouldTranslateUnavailableToInventoryUnavailable() {
        RuntimeException translated = client.translate(statusException(Status.UNAVAILABLE), PRODUCT_ID);

        assertThat(translated).isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void shouldTranslateDeadlineExceededToInventoryUnavailable() {
        // A timeout is a availability problem too: the order is fine, the
        // dependency did not answer in time, so the caller may retry.
        RuntimeException translated = client.translate(statusException(Status.DEADLINE_EXCEEDED), PRODUCT_ID);

        assertThat(translated).isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void shouldTreatInvalidArgumentAsADefectOnThisSide() {
        // REST validation already guarantees a positive quantity, so INVALID_ARGUMENT
        // means this service built a malformed request. That is a server fault, not
        // something to report to the client as a business outcome.
        RuntimeException translated = client.translate(statusException(Status.INVALID_ARGUMENT), PRODUCT_ID);

        assertThat(translated).isInstanceOf(IllegalStateException.class);
        assertThat(translated)
                .isNotInstanceOf(ProductNotFoundException.class)
                .isNotInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void shouldPreserveTheOriginalStatusExceptionAsCause() {
        StatusRuntimeException original = statusException(Status.NOT_FOUND);

        RuntimeException translated = client.translate(original, PRODUCT_ID);

        assertThat(translated).hasCause(original);
    }

    private StatusRuntimeException statusException(Status status) {
        return status.withDescription("from test").asRuntimeException();
    }
}

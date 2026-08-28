package com.pmfml.orderflow.orderservice.grpc;

import com.pmfml.orderflow.common.grpc.inventory.InventoryServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to register gRPC client stubs as Spring Beans.
 *
 * <p>By extracting the {@code @GrpcClient} field injection into this
 * configuration class, we enable constructor injection in our service
 * classes (like {@link GrpcInventoryClient}). This makes the services
 * much easier to unit test, as the stubs can be mocked directly without
 * relying on reflection or Spring Context tests.
 */
@Configuration
public class GrpcClientConfig {

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub() {
        return this.inventoryStub;
    }
}

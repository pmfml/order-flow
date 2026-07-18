package com.pmfml.orderflow.inventoryservice.repositories;

import com.pmfml.orderflow.inventoryservice.entities.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }
    
    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindProductByTenantId() {
        // Given
        Product product = Product.builder()
                .tenantId("tenant-1")
                .sku("SKU-100")
                .name("Wireless Mouse")
                .category("Electronics")
                .price(new BigDecimal("29.99"))
                .stockQuantity(150)
                .attributes(Map.of("color", "black", "wireless", "true"))
                .build();

        // When
        Product saved = productRepository.save(product);
        Optional<Product> found = productRepository.findByIdAndTenantId(saved.getId(), "tenant-1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Wireless Mouse");
        assertThat(found.get().getAttributes()).containsEntry("color", "black");
        
        // Also verify the audit dates populated by Mongo auditing
        // Note: Auditing requires @EnableMongoAuditing in the main app,
        // so in this isolated test, dates might be null unless we enable it.
    }
    
    @Test
    void shouldNotFindProductFromDifferentTenant() {
        // Given
        Product product = Product.builder()
                .tenantId("tenant-1")
                .sku("SKU-200")
                .name("Keyboard")
                .price(new BigDecimal("49.99"))
                .stockQuantity(50)
                .build();
        Product saved = productRepository.save(product);

        // When
        Optional<Product> found = productRepository.findByIdAndTenantId(saved.getId(), "tenant-2");

        // Then
        assertThat(found).isEmpty();
    }
}

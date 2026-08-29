package com.pmfml.orderflow.inventoryservice.config;

import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Automatically seeds the MongoDB catalog database with default products
 * for local development and testing, if empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class MongoDbSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            log.info("[MongoSeeder] Seeding default products into catalog...");

            // Seed products for default tenant (dev-tenant)
            Product prod1 = Product.builder()
                    .id("prod-macbook")
                    .tenantId("dev-tenant")
                    .sku("SKU-MACBOOK")
                    .name("MacBook Pro 14\"")
                    .category("Electronics")
                    .price(new BigDecimal("1999.99"))
                    .stockQuantity(50)
                    .attributes(Map.of("color", "space-gray", "ram", "16GB", "ssd", "512GB"))
                    .build();

            Product prod2 = Product.builder()
                    .id("prod-iphone")
                    .tenantId("dev-tenant")
                    .sku("SKU-IPHONE15")
                    .name("iPhone 15 Pro")
                    .category("Electronics")
                    .price(new BigDecimal("999.99"))
                    .stockQuantity(100)
                    .attributes(Map.of("color", "titanium", "storage", "128GB"))
                    .build();

            Product prod3 = Product.builder()
                    .id("prod-tshirt")
                    .tenantId("dev-tenant")
                    .sku("SKU-TSHIRT-BLACK")
                    .name("Classic Cotton T-Shirt")
                    .category("Clothing")
                    .price(new BigDecimal("29.99"))
                    .stockQuantity(500)
                    .attributes(Map.of("color", "black", "size", "L"))
                    .build();

            productRepository.saveAll(List.of(prod1, prod2, prod3));
            log.info("[MongoSeeder] Seeding completed. Default products available for dev-tenant: prod-macbook, prod-iphone, prod-tshirt");
        } else {
            log.info("[MongoSeeder] Product catalog already contains data. Skipping seeding.");
        }
    }
}

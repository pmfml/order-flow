package com.pmfml.orderflow.inventoryservice.repositories;

import com.pmfml.orderflow.inventoryservice.entities.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {
    
    Optional<Product> findByIdAndTenantId(String id, String tenantId);
    
    Optional<Product> findBySkuAndTenantId(String sku, String tenantId);
}

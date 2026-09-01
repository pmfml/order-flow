package com.pmfml.orderflow.inventoryservice.services;

import com.pmfml.orderflow.inventoryservice.dtos.ProductResponse;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.exceptions.ProductNotFoundException;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#tenantId + ':list'")
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByTenant(String tenantId) {
        log.info("[ProductQuery] Listing products for tenantId: {}", tenantId);
        
        return productRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "products", key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(String id, String tenantId) {
        log.info("[ProductQuery] Fetching product: id={}, tenantId={}", id, tenantId);
        
        return productRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getAttributes(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

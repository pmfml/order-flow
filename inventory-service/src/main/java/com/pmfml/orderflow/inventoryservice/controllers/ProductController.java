package com.pmfml.orderflow.inventoryservice.controllers;

import com.pmfml.orderflow.inventoryservice.dtos.ProductResponse;
import com.pmfml.orderflow.inventoryservice.services.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        
        log.info("[REST] Received request to list products for tenantId: {}", tenantId);
        List<ProductResponse> products = productService.getProductsByTenant(tenantId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
            
        log.info("[REST] Received request to fetch product {} for tenantId: {}", id, tenantId);
        ProductResponse product = productService.getProductById(id, tenantId);
        return ResponseEntity.ok(product);
    }
}

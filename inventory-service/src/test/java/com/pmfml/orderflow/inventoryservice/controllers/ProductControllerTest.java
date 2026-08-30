package com.pmfml.orderflow.inventoryservice.controllers;

import com.pmfml.orderflow.inventoryservice.dtos.ProductResponse;
import com.pmfml.orderflow.inventoryservice.exceptions.ProductNotFoundException;
import com.pmfml.orderflow.inventoryservice.services.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void shouldListProductsForTenant() throws Exception {
        ProductResponse prod1 = new ProductResponse(
                "prod-1", "SKU-1", "Widget A", "Widgets",
                new BigDecimal("10.00"), 100, Map.of(), Instant.now(), Instant.now());
        
        ProductResponse prod2 = new ProductResponse(
                "prod-2", "SKU-2", "Widget B", "Widgets",
                new BigDecimal("20.00"), 50, Map.of(), Instant.now(), Instant.now());

        given(productService.getProductsByTenant("tenant-1"))
                .willReturn(List.of(prod1, prod2));

        mockMvc.perform(get("/v1/products")
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("prod-1"))
                .andExpect(jsonPath("$[1].id").value("prod-2"));
    }

    @Test
    void shouldReturnEmptyListWhenNoProducts() throws Exception {
        given(productService.getProductsByTenant("tenant-1"))
                .willReturn(List.of());

        mockMvc.perform(get("/v1/products")
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldFailListingWhenTenantMissing() throws Exception {
        mockMvc.perform(get("/v1/products"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetProductById() throws Exception {
        ProductResponse prod = new ProductResponse(
                "prod-1", "SKU-1", "Widget A", "Widgets",
                new BigDecimal("10.00"), 100, Map.of(), Instant.now(), Instant.now());

        given(productService.getProductById("prod-1", "tenant-1"))
                .willReturn(prod);

        mockMvc.perform(get("/v1/products/prod-1")
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prod-1"))
                .andExpect(jsonPath("$.name").value("Widget A"));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        given(productService.getProductById("prod-missing", "tenant-1"))
                .willThrow(new ProductNotFoundException("prod-missing"));

        mockMvc.perform(get("/v1/products/prod-missing")
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product Not Found"))
                .andExpect(jsonPath("$.productId").value("prod-missing"));
    }

    @Test
    void shouldFailGettingProductByIdWhenTenantMissing() throws Exception {
        mockMvc.perform(get("/v1/products/prod-1"))
                .andExpect(status().isBadRequest());
    }
}

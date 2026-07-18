package com.pmfml.orderflow.inventoryservice.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * MongoDB Document representing a catalog product.
 * 
 * <p>A flexible schema is used here because different categories
 * (e.g., Electronics vs Clothing) have entirely different attribute sets.
 */
@Document(collection = "products")
@CompoundIndex(name = "tenant_sku_idx", def = "{'tenantId': 1, 'sku': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;
    
    private String tenantId;
    
    private String sku;
    
    private String name;
    
    private String category;
    
    private BigDecimal price;
    
    private Integer stockQuantity;
    
    /**
     * Flexible attributes such as {"size": "M", "color": "blue"}
     * or {"voltage": "220v", "warranty": "1 year"}.
     */
    private Map<String, String> attributes;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
}

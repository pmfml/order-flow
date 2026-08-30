package com.pmfml.orderflow.inventoryservice.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        Integer stockQuantity,
        Map<String, String> attributes,
        Instant createdAt,
        Instant updatedAt
) {
}

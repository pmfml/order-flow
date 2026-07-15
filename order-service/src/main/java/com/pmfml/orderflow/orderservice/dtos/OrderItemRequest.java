package com.pmfml.orderflow.orderservice.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for a single line item.
 *
 * <p>Notice that pricing and naming are NOT provided by the client to prevent fraud.
 * The backend will fetch the authoritative price and name from the Inventory service.
 */
public record OrderItemRequest(

        @NotBlank(message = "Product ID cannot be blank")
        @Size(max = 50, message = "Product ID must not exceed 50 characters")
        String productId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {
}

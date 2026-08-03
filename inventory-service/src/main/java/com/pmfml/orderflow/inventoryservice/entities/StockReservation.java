package com.pmfml.orderflow.inventoryservice.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents stock temporarily held for an order that is currently in flight.
 *
 * <p>When an order is created, the requested quantities are subtracted from
 * the main {@link Product} document and a {@code StockReservation} is created.
 * If the saga completes successfully, the reservation transitions to {@code CONFIRMED}.
 * If the saga is cancelled, the reservation transitions to {@code RELEASED} and
 * the stock is returned to the product.
 *
 * <p>Includes a TTL index on {@code expiresAt} as a safety net: if the Saga gets
 * stuck and no confirmation or cancellation ever arrives, MongoDB will automatically
 * delete the document. A Change Stream listener (or periodic job) can react to this
 * deletion to restore the stock.
 */
@Document(collection = "stock_reservations")
@CompoundIndex(name = "idx_order_tenant", def = "{'orderId': 1, 'tenantId': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    @Id
    private String id;

    @Indexed
    private String orderId;

    @Indexed
    private String tenantId;

    @Indexed
    private String productId;

    private int quantity;

    private ReservationStatus status;

    private Instant createdAt;

    /**
     * TTL index field. MongoDB will automatically delete this document when
     * the current time surpasses this timestamp.
     * Only valid for RESERVED status (a periodic job handles the actual logic to release stock if this expires).
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;
}

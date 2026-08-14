package com.pmfml.orderflow.orderservice.entities;

import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Root aggregate representing a customer order within a tenant.
 *
 * <p>An Order starts as {@link OrderStatus#PENDING} and transitions to
 * {@code CONFIRMED} or {@code CANCELLED} based on the choreographed Saga outcome.
 *
 * <p><strong>State machine:</strong> only two transitions are valid:
 * <ul>
 *     <li>{@code PENDING → CONFIRMED} — via {@link #confirm()}</li>
 *     <li>{@code PENDING → CANCELLED} — via {@link #cancel()}</li>
 * </ul>
 * Any other transition throws {@link IllegalStateException}. There is no
 * public setter on {@code status}; all mutations go through these
 * business-intent methods so that out-of-order Saga events cannot corrupt
 * the lifecycle.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Setter
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    /**
     * Transitions this order to {@link OrderStatus#CONFIRMED}.
     *
     * @throws IllegalStateException if the current status is not {@code PENDING}
     */
    public void confirm() {
        assertPending("confirm");
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Records the time the payment was captured.
     *
     * @param captureTime the time of capture
     */
    public void capture(Instant captureTime) {
        if (this.status != OrderStatus.CONFIRMED && this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot capture order " + id + ": current status is " + status + ", expected PENDING or CONFIRMED");
        }
        this.capturedAt = captureTime;
    }

    /**
     * Transitions this order to {@link OrderStatus#CANCELLED}.
     *
     * @throws IllegalStateException if the current status is not {@code PENDING}
     */
    public void cancel() {
        assertPending("cancel");
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * Adds an item to this order and sets the bidirectional relationship.
     *
     * @param item the order item to add
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    private void assertPending(String action) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot %s order %s: current status is %s, expected PENDING"
                            .formatted(action, id, status));
        }
    }
}

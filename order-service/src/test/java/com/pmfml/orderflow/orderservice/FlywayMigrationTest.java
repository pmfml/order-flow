package com.pmfml.orderflow.orderservice;

import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OrderItem;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.repositories.OrderRepository;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Flyway migrations that build the production schema.
 *
 * <p>Every other test in this module runs with {@code spring.flyway.enabled=false}
 * and {@code ddl-auto=create-drop}, letting Hibernate generate the schema from the
 * entities. That is fast, but it means the SQL under {@code db/migration} is never
 * executed by the suite: a column renamed in an entity but not in a migration, or
 * a constraint present in one and absent from the other, would go unnoticed and
 * only surface at deployment.
 *
 * <p>This test closes that gap by running the real migrations against a real
 * PostgreSQL and setting {@code ddl-auto=validate}. Context startup alone is a
 * meaningful assertion: Hibernate refuses to boot when a mapped table or column is
 * missing from the migrated schema, so a drifted entity fails here.
 *
 * <p>Validation does not cover everything, though — it checks that columns exist,
 * not that their precision or constraints match. The remaining tests therefore
 * round-trip real values and probe constraints with raw SQL, bypassing Hibernate's
 * own checks so that only the database's DDL can satisfy them.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldApplyEveryMigrationWithNothingPendingOrFailed() {
        var info = flyway.info();

        // Deliberately version-agnostic so adding a migration does not require
        // editing this assertion.
        assertThat(info.applied())
                .as("migrations recorded in flyway_schema_history")
                .isNotEmpty();
        assertThat(info.pending())
                .as("migrations Flyway found but did not run")
                .isEmpty();
        assertThat(info.applied())
                .allSatisfy(migration -> assertThat(migration.getState().isFailed())
                        .as("migration %s must not be in a failed state", migration.getVersion())
                        .isFalse());
        assertThat(info.current()).isNotNull();
    }

    @Test
    @Transactional
    void shouldRoundTripOrderAggregatePreservingMonetaryScale() {
        Order order = Order.builder()
                .tenantId("tenant-migration")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("10.99"))
                .build();
        order.addItem(OrderItem.builder()
                .productId("prod-1")
                .productName("Widget")
                .quantity(3)
                .unitPrice(new BigDecimal("3.66"))
                .build());

        Order saved = orderRepository.saveAndFlush(order);

        // Force a real read back from the database rather than the identity map.
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo("tenant-migration");
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();

        // NUMERIC(19,2) in V1 against precision=19/scale=2 on the entity: a
        // mismatch here would silently round money.
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("10.99");
        assertThat(reloaded.getTotalAmount().scale()).isEqualTo(2);

        assertThat(reloaded.getItems()).hasSize(1);
        OrderItem reloadedItem = reloaded.getItems().get(0);
        assertThat(reloadedItem.getProductName()).isEqualTo("Widget");
        assertThat(reloadedItem.getQuantity()).isEqualTo(3);
        assertThat(reloadedItem.getUnitPrice()).isEqualByComparingTo("3.66");
    }

    @Test
    @Transactional
    void shouldRoundTripOutboxEventIncludingTheTenantColumnAddedInV2() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .tenantId("tenant-migration")
                .eventType(EventTypes.ORDER_CREATED)
                .payload("{\"orderId\":\"abc\"}")
                .build();

        OutboxEvent saved = outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo("tenant-migration");
        assertThat(reloaded.getEventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getProcessedAt()).isNull();
    }

    @Test
    void shouldRejectOutboxEventWithoutTenantIdBecauseV2AddedNotNull() {
        // Raw SQL on purpose: Hibernate would reject this from the @Column
        // metadata, which would pass whether or not the constraint reached the
        // database. Only the migrated DDL can fail this insert.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "Order",
                UUID.randomUUID(),
                EventTypes.ORDER_CREATED,
                "{}",
                Instant.now().atOffset(java.time.ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectNonPositiveQuantityBecauseV1DefinedACheckConstraint() {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO orders (id, tenant_id, status, total_amount)
                VALUES (?, ?, ?, ?)
                """, orderId, "tenant-migration", OrderStatus.PENDING.name(), new BigDecimal("0.00"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), orderId, "prod-1", "Widget", 0, new BigDecimal("1.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

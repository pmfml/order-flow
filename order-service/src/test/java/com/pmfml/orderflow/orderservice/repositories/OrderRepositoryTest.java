package com.pmfml.orderflow.orderservice.repositories;

import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OrderItem;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSaveAndRetrieveOrderWithItems() {
        Order order = Order.builder()
                .tenantId("tenant-1")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("99.90"))
                .build();

        OrderItem item = OrderItem.builder()
                .productId("prod-abc")
                .productName("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("49.95"))
                .build();

        order.addItem(item);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getId()).isNotNull();
        assertThat(found.getItems().get(0).getProductName()).isEqualTo("Widget");
    }

    @Test
    void shouldFilterOrdersByTenantId() {
        Order tenantOneOrder = Order.builder()
                .tenantId("tenant-1")
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN)
                .build();

        Order tenantTwoOrder = Order.builder()
                .tenantId("tenant-2")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.ONE)
                .build();

        orderRepository.saveAll(List.of(tenantOneOrder, tenantTwoOrder));

        List<Order> results = orderRepository.findByTenantIdOrderByCreatedAtDesc("tenant-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void shouldFindByIdAndTenantId() {
        Order order = Order.builder()
                .tenantId("tenant-1")
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN)
                .build();

        Order saved = orderRepository.save(order);

        Optional<Order> found = orderRepository.findByIdAndTenantId(saved.getId(), "tenant-1");
        Optional<Order> notFound = orderRepository.findByIdAndTenantId(saved.getId(), "wrong-tenant");

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    void shouldCascadeDeleteOrderItems() {
        Order order = Order.builder()
                .tenantId("tenant-1")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("25.00"))
                .build();

        order.addItem(OrderItem.builder()
                .productId("prod-1")
                .productName("Gadget")
                .quantity(1)
                .unitPrice(new BigDecimal("25.00"))
                .build());

        Order saved = orderRepository.save(order);
        orderRepository.delete(saved);
        orderRepository.flush();

        assertThat(orderRepository.findById(saved.getId())).isEmpty();
    }
}

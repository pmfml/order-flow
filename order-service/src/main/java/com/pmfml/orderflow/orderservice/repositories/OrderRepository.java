package com.pmfml.orderflow.orderservice.repositories;

import com.pmfml.orderflow.orderservice.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Order} aggregate persistence.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Lists all orders belonging to a tenant, most recent first.
     *
     * @param tenantId the tenant identifier
     * @return ordered list of orders
     */
    List<Order> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Finds a specific order scoped to its tenant.
     *
     * @param id       the order UUID
     * @param tenantId the tenant identifier
     * @return the order if it exists and belongs to the tenant
     */
    Optional<Order> findByIdAndTenantId(UUID id, String tenantId);
}

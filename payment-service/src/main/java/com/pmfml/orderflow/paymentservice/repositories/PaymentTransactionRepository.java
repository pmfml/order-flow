package com.pmfml.orderflow.paymentservice.repositories;

import com.pmfml.orderflow.paymentservice.entities.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByOrderIdAndTenantId(UUID orderId, String tenantId);
}

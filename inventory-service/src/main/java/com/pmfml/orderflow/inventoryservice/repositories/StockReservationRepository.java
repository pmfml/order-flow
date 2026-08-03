package com.pmfml.orderflow.inventoryservice.repositories;

import com.pmfml.orderflow.inventoryservice.entities.StockReservation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends MongoRepository<StockReservation, String> {

    List<StockReservation> findByOrderIdAndTenantId(String orderId, String tenantId);
    
    Optional<StockReservation> findByOrderIdAndProductIdAndTenantId(String orderId, String productId, String tenantId);
}

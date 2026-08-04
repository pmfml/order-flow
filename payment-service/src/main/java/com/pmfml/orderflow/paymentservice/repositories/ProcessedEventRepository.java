package com.pmfml.orderflow.paymentservice.repositories;

import com.pmfml.orderflow.paymentservice.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}

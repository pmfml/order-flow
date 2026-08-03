package com.pmfml.orderflow.inventoryservice.repositories;

import com.pmfml.orderflow.inventoryservice.entities.ProcessedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}

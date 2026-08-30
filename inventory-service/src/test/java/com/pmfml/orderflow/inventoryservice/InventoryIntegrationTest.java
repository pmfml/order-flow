package com.pmfml.orderflow.inventoryservice;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.inventoryservice.entities.ProcessedEvent;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.entities.ReservationStatus;
import com.pmfml.orderflow.inventoryservice.entities.StockReservation;
import com.pmfml.orderflow.inventoryservice.repositories.ProcessedEventRepository;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import com.pmfml.orderflow.inventoryservice.repositories.StockReservationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.awaitility.Awaitility;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EmbeddedKafka(partitions = 1, topics = {EventTypes.ORDER_CREATED, EventTypes.INVENTORY_RESERVED, EventTypes.INVENTORY_RESERVATION_FAILED})
class InventoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private final String tenantId = "test-tenant";

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        stockReservationRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldReserveStockAndPublishReservedEvent() throws Exception {
        // Arrange
        Product product = Product.builder()
                .tenantId(tenantId)
                .sku("SKU-INT")
                .stockQuantity(10)
                .price(BigDecimal.TEN)
                .build();
        productRepository.save(product);

        UUID orderId = UUID.randomUUID();
        EventEnvelope orderCreatedEvent = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.ORDER_CREATED,
                tenantId,
                Instant.now(),
                Map.of(
                        "orderId", orderId.toString(),
                        "totalAmount", 30,
                        "items", List.of(
                                Map.of(
                                        "productId", product.getId(),
                                        "quantity", 3
                                )
                        )
                )
        );

        // Act
        String message = objectMapper.writeValueAsString(orderCreatedEvent);
        kafkaTemplate.send(EventTypes.ORDER_CREATED, orderId.toString(), message).get();

        // Wait for async processing
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
                    assertThat(updatedProduct.getStockQuantity()).isEqualTo(7);

                    List<StockReservation> reservations = stockReservationRepository.findByOrderIdAndTenantId(orderId.toString(), tenantId);
                    assertThat(reservations).hasSize(1);
                    assertThat(reservations.getFirst().getStatus()).isEqualTo(ReservationStatus.RESERVED);
                });

        List<ProcessedEvent> processed = processedEventRepository.findAll();
        assertThat(processed).hasSize(1);
        assertThat(processed.getFirst().getId()).isEqualTo(orderCreatedEvent.eventId().toString());
    }
}

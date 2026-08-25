package com.pmfml.orderflow.paymentservice;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.paymentservice.entities.PaymentStatus;
import com.pmfml.orderflow.paymentservice.entities.PaymentTransaction;
import com.pmfml.orderflow.paymentservice.entities.ProcessedEvent;
import com.pmfml.orderflow.paymentservice.gateway.PaymentGateway;
import com.pmfml.orderflow.paymentservice.gateway.PaymentResult;
import com.pmfml.orderflow.paymentservice.repositories.PaymentTransactionRepository;
import com.pmfml.orderflow.paymentservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.awaitility.Awaitility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentIntegrationTest {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private final String tenantId = "test-tenant";

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldAuthorizePaymentAndPersistTransaction() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String stripeIntentId = "pi_test_" + UUID.randomUUID();

        when(paymentGateway.authorize(eq(orderId.toString()), eq(tenantId), any(BigDecimal.class)))
                .thenReturn(PaymentResult.authorized(stripeIntentId));

        EventEnvelope inventoryReservedEvent = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.INVENTORY_RESERVED,
                tenantId,
                Instant.now(),
                Map.of(
                        "orderId", orderId.toString(),
                        "totalAmount", 150.50
                )
        );

        // Act
        String message = objectMapper.writeValueAsString(inventoryReservedEvent);
        kafkaTemplate.send(EventTypes.INVENTORY_RESERVED, orderId.toString(), message).get();

        // Wait and Assert
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
                    assertThat(transactions).hasSize(1);

                    PaymentTransaction tx = transactions.getFirst();
                    assertThat(tx.getOrderId()).isEqualTo(orderId);
                    assertThat(tx.getTenantId()).isEqualTo(tenantId);
                    assertThat(tx.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
                    assertThat(tx.getStripePaymentIntentId()).isEqualTo(stripeIntentId);
                    assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("150.5"));

                    List<ProcessedEvent> processed = processedEventRepository.findAll();
                    assertThat(processed).hasSize(1);
                    assertThat(processed.getFirst().getEventId()).isEqualTo(inventoryReservedEvent.eventId());
                });
    }

    @Test
    void shouldRecordFailedPayment() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();

        when(paymentGateway.authorize(eq(orderId.toString()), eq(tenantId), any(BigDecimal.class)))
                .thenReturn(PaymentResult.failed("Card declined"));

        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.INVENTORY_RESERVED,
                tenantId,
                Instant.now(),
                Map.of(
                        "orderId", orderId.toString(),
                        "totalAmount", 99.99
                )
        );

        // Act
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTypes.INVENTORY_RESERVED, orderId.toString(), message).get();

        // Wait and Assert
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
                    assertThat(transactions).hasSize(1);
                    assertThat(transactions.getFirst().getStatus()).isEqualTo(PaymentStatus.FAILED);
                });
    }

    @Test
    void shouldSkipDuplicateEvent() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String stripeIntentId = "pi_test_" + UUID.randomUUID();

        when(paymentGateway.authorize(eq(orderId.toString()), eq(tenantId), any(BigDecimal.class)))
                .thenReturn(PaymentResult.authorized(stripeIntentId));

        EventEnvelope event = new EventEnvelope(
                eventId,
                EventTypes.INVENTORY_RESERVED,
                tenantId,
                Instant.now(),
                Map.of(
                        "orderId", orderId.toString(),
                        "totalAmount", 200.00
                )
        );

        String message = objectMapper.writeValueAsString(event);

        // Act — send the same event twice
        kafkaTemplate.send(EventTypes.INVENTORY_RESERVED, orderId.toString(), message).get();
        
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(paymentTransactionRepository.findAll()).hasSize(1));

        kafkaTemplate.send(EventTypes.INVENTORY_RESERVED, orderId.toString(), message).get();
        
        // Wait a small amount to ensure consumer processed it (it should skip)
        // Since we can't await a "no-op", we await 1 second without error.
        Thread.sleep(1000); 

        // Assert — only one transaction created (idempotent)
        List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);

        List<ProcessedEvent> processed = processedEventRepository.findAll();
        assertThat(processed).hasSize(1);
    }
}

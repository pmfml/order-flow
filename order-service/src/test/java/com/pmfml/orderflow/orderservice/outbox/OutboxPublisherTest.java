package com.pmfml.orderflow.orderservice.outbox;

import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;

    private OutboxPublisher outboxPublisher;

    private static final String TOPIC = "orders.created";

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate);
        // Inject the topic value via reflection since @Value won't be processed in unit tests
        try {
            var topicField = OutboxPublisher.class.getDeclaredField("topic");
            topicField.setAccessible(true);
            topicField.set(outboxPublisher, TOPIC);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject topic field", e);
        }
    }

    @Test
    void shouldPublishPendingEventsAndMarkAsProcessed() {
        // Given
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(aggregateId)
                .eventType("orders.created")
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .createdAt(Instant.now())
                .build();

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(eq(TOPIC), eq(aggregateId.toString()), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();

        // Then — event was sent to Kafka with correct key and payload
        verify(kafkaTemplate).send(TOPIC, aggregateId.toString(), event.getPayload());

        // Then — event was marked as processed
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getProcessedAt()).isNotNull();
        assertThat(savedEvent.getId()).isEqualTo(event.getId());
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        // Given
        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of());

        // When
        outboxPublisher.pollAndPublish();

        // Then — no Kafka interaction
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldNotMarkAsProcessedWhenKafkaSendThrows() {
        // Given
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(aggregateId)
                .eventType("orders.created")
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .createdAt(Instant.now())
                .build();

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(eq(TOPIC), eq(aggregateId.toString()), any()))
                .willThrow(new RuntimeException("Kafka is down"));

        // When
        outboxPublisher.pollAndPublish();

        // Then — event was NOT marked as processed (will be retried)
        verify(outboxEventRepository, never()).save(any());
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void shouldPublishMultipleEventsInOrder() {
        // Given
        OutboxEvent event1 = buildEvent("orders.created");
        OutboxEvent event2 = buildEvent("orders.created");

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event1, event2));
        given(kafkaTemplate.send(eq(TOPIC), any(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();

        // Then — both events published and marked
        verify(kafkaTemplate, times(2)).send(eq(TOPIC), any(), any());
        verify(outboxEventRepository, times(2)).save(any());
    }

    private OutboxEvent buildEvent(String eventType) {
        UUID aggregateId = UUID.randomUUID();
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .createdAt(Instant.now())
                .build();
    }
}

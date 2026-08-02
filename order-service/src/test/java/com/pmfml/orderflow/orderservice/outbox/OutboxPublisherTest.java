package com.pmfml.orderflow.orderservice.outbox;

import com.pmfml.orderflow.common.events.EventTypes;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private OutboxPublisher outboxPublisher;

    private static final String TENANT_ID = "tenant-123";

    /** Short on purpose: one test drives a future that never completes. */
    private static final long SEND_TIMEOUT_MS = 200;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(
                outboxEventRepository, kafkaTemplate, objectMapper, SEND_TIMEOUT_MS);
    }

    @Test
    void shouldPublishPendingEventsAndMarkAsProcessed() {
        // Given
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(eq(EventTypes.ORDER_CREATED), eq(event.getAggregateId().toString()), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();

        // Then — routed to the topic named after the event type, keyed by aggregate
        verify(kafkaTemplate).send(
                eq(EventTypes.ORDER_CREATED),
                eq(event.getAggregateId().toString()),
                any());

        // Then — event was marked as processed
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getProcessedAt()).isNotNull();
        assertThat(savedEvent.getId()).isEqualTo(event.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWrapPayloadInTheDocumentedEnvelope() {
        // Given
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();

        // Then
        verify(kafkaTemplate).send(any(), any(), messageCaptor.capture());
        Map<String, Object> envelope = objectMapper.readValue(messageCaptor.getValue(), Map.class);

        assertThat(envelope).containsOnlyKeys("eventId", "eventType", "tenantId", "occurredAt", "payload");

        // eventId is the outbox row id, which is what makes consumer deduplication
        // work across republications of the same row.
        assertThat(envelope).containsEntry("eventId", event.getId().toString());
        assertThat(envelope).containsEntry("eventType", EventTypes.ORDER_CREATED);
        assertThat(envelope).containsEntry("tenantId", TENANT_ID);

        // §7.2 documents occurredAt as an ISO-8601 instant, not an epoch number,
        // and it reflects when the event was recorded rather than published.
        assertThat(envelope.get("occurredAt")).isInstanceOf(String.class);
        assertThat(Instant.parse((String) envelope.get("occurredAt")))
                .isEqualTo(event.getCreatedAt());

        // The stored payload nests as a JSON object, not an escaped string
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload).containsEntry("orderId", event.getAggregateId().toString());
    }

    @Test
    void shouldReuseTheSameEventIdWhenRepublishingTheSameRow() {
        // Given — the same unprocessed row is picked up on two consecutive cycles,
        // which is exactly the at-least-once scenario consumers must deduplicate.
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event), List.of(event));
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();
        outboxPublisher.pollAndPublish();

        // Then — both messages carry an identical eventId
        verify(kafkaTemplate, times(2)).send(any(), any(), messageCaptor.capture());
        List<String> messages = messageCaptor.getAllValues();
        assertThat(messages).hasSize(2);
        assertThat(readEventId(messages.get(0)))
                .isEqualTo(readEventId(messages.get(1)))
                .isEqualTo(event.getId().toString());
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
    void shouldNotMarkAsProcessedWhenKafkaFailsAsynchronously() {
        // Given — the broker accepts the call but rejects the record afterwards,
        // which is how most real publish failures surface: the send() call itself
        // returns normally and only the future completes exceptionally.
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(CompletableFuture.failedFuture(
                        new RuntimeException("broker rejected the record")));

        // When
        outboxPublisher.pollAndPublish();

        // Then — the event must stay pending so the next cycle retries it.
        // Marking it processed here would drop the event permanently, defeating
        // the guarantee the Outbox pattern exists to provide.
        verify(outboxEventRepository, never()).save(any());
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void shouldNotMarkAsProcessedWhenKafkaSendNeverCompletes() {
        // Given — a future that never completes, standing in for an unreachable
        // or hung broker. The publisher must give up rather than block forever.
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(new CompletableFuture<>());

        // When
        outboxPublisher.pollAndPublish();

        // Then
        verify(outboxEventRepository, never()).save(any());
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void shouldNotMarkAsProcessedWhenKafkaSendThrows() {
        // Given
        OutboxEvent event = buildEvent(EventTypes.ORDER_CREATED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(event));
        given(kafkaTemplate.send(any(), any(), any()))
                .willThrow(new RuntimeException("Kafka is down"));

        // When
        outboxPublisher.pollAndPublish();

        // Then — event was NOT marked as processed (will be retried)
        verify(outboxEventRepository, never()).save(any());
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void shouldRouteEachEventToTheTopicNamedAfterItsType() {
        // Given — a single outbox holding two different event types, which is why a
        // single configured topic could not work.
        OutboxEvent created = buildEvent(EventTypes.ORDER_CREATED);
        OutboxEvent cancelled = buildEvent(EventTypes.ORDER_CANCELLED);

        given(outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(created, cancelled));
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPublisher.pollAndPublish();

        // Then
        verify(kafkaTemplate).send(eq(EventTypes.ORDER_CREATED), any(), any());
        verify(kafkaTemplate).send(eq(EventTypes.ORDER_CANCELLED), any(), any());
        verify(outboxEventRepository, times(2)).save(any());
    }

    private String readEventId(String message) {
        return (String) objectMapper.readValue(message, Map.class).get("eventId");
    }

    private OutboxEvent buildEvent(String eventType) {
        UUID aggregateId = UUID.randomUUID();
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(aggregateId)
                .tenantId(TENANT_ID)
                .eventType(eventType)
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .build();
    }
}

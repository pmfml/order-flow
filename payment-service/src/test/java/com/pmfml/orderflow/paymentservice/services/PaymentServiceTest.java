package com.pmfml.orderflow.paymentservice.services;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.paymentservice.controllers.WebhookPayload;
import com.pmfml.orderflow.paymentservice.dtos.PaymentResponse;
import com.pmfml.orderflow.paymentservice.entities.PaymentStatus;
import com.pmfml.orderflow.paymentservice.entities.PaymentTransaction;
import com.pmfml.orderflow.paymentservice.exceptions.PaymentNotFoundException;
import com.pmfml.orderflow.paymentservice.gateway.PaymentGateway;
import com.pmfml.orderflow.paymentservice.gateway.PaymentResult;
import com.pmfml.orderflow.paymentservice.repositories.PaymentTransactionRepository;
import com.pmfml.orderflow.paymentservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<PaymentTransaction> transactionCaptor;

    private static final String TENANT_ID = "tenant-unit";
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String ORDER_ID_STR = ORDER_ID.toString();

    // ---------------------------------------------------------------
    // handleInventoryReserved
    // ---------------------------------------------------------------

    @Test
    void shouldAuthorizePaymentSuccessfully() throws Exception {
        EventEnvelope event = inventoryReservedEvent(4501.5);

        given(processedEventRepository.existsById(event.eventId())).willReturn(false);
        given(paymentGateway.authorize(ORDER_ID_STR, TENANT_ID, new BigDecimal("4501.5")))
                .willReturn(PaymentResult.authorized("pi_success"));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        paymentService.handleInventoryReserved(event);

        // Transaction should be persisted with AUTHORIZED status
        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        PaymentTransaction saved = transactionCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("4501.5"));
        assertThat(saved.getStripePaymentIntentId()).isEqualTo("pi_success");

        // Should publish payment.authorized
        verify(kafkaTemplate).send(eq(EventTypes.PAYMENT_AUTHORIZED), eq(ORDER_ID_STR), anyString());
    }

    @Test
    void shouldPersistFailedPaymentWhenGatewayRejects() throws Exception {
        EventEnvelope event = inventoryReservedEvent(100.0);

        given(processedEventRepository.existsById(event.eventId())).willReturn(false);
        given(paymentGateway.authorize(ORDER_ID_STR, TENANT_ID, new BigDecimal("100.0")))
                .willReturn(PaymentResult.failed("Card declined"));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        paymentService.handleInventoryReserved(event);

        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

        verify(kafkaTemplate).send(eq(EventTypes.PAYMENT_FAILED), eq(ORDER_ID_STR), anyString());
    }

    @Test
    void shouldSkipAlreadyProcessedEvent() {
        EventEnvelope event = inventoryReservedEvent(100.0);

        given(processedEventRepository.existsById(event.eventId())).willReturn(true);

        paymentService.handleInventoryReserved(event);

        verify(paymentGateway, never()).authorize(anyString(), anyString(), any());
        verify(paymentTransactionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldHandleConcurrentDuplicateGracefully() throws Exception {
        EventEnvelope event = inventoryReservedEvent(100.0);

        given(processedEventRepository.existsById(event.eventId())).willReturn(false);
        given(paymentGateway.authorize(ORDER_ID_STR, TENANT_ID, new BigDecimal("100.0")))
                .willReturn(PaymentResult.authorized("pi_concurrent"));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(processedEventRepository).save(any(com.pmfml.orderflow.paymentservice.entities.ProcessedEvent.class));

        paymentService.handleInventoryReserved(event);

        // Transaction is saved, but the outcome event is NOT published (early return)
        verify(paymentTransactionRepository).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldDefaultToZeroWhenTotalAmountMissing() throws Exception {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.INVENTORY_RESERVED,
                TENANT_ID,
                Instant.now(),
                Map.of("orderId", ORDER_ID_STR)
        );

        given(processedEventRepository.existsById(event.eventId())).willReturn(false);
        given(paymentGateway.authorize(ORDER_ID_STR, TENANT_ID, BigDecimal.ZERO))
                .willReturn(PaymentResult.failed("Zero amount"));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        paymentService.handleInventoryReserved(event);

        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------
    // handleWebhook
    // ---------------------------------------------------------------

    @Test
    void shouldProcessCapturedWebhook() throws Exception {
        WebhookPayload payload = webhookPayload("CAPTURED");
        PaymentTransaction existingTx = existingTransaction(PaymentStatus.AUTHORIZED);

        given(paymentTransactionRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Optional.of(existingTx));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        paymentService.handleWebhook(payload);

        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(kafkaTemplate).send(eq(EventTypes.PAYMENT_CAPTURED), eq(ORDER_ID_STR), anyString());
    }

    @Test
    void shouldProcessFailedWebhook() throws Exception {
        WebhookPayload payload = webhookPayload("FAILED");
        PaymentTransaction existingTx = existingTransaction(PaymentStatus.AUTHORIZED);

        given(paymentTransactionRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Optional.of(existingTx));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        paymentService.handleWebhook(payload);

        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(kafkaTemplate).send(eq(EventTypes.PAYMENT_FAILED), eq(ORDER_ID_STR), anyString());
    }

    @Test
    void shouldThrowWhenWebhookOrderNotFound() {
        WebhookPayload payload = webhookPayload("CAPTURED");

        given(paymentTransactionRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.handleWebhook(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORDER_ID_STR);
    }

    // ---------------------------------------------------------------
    // getPaymentByOrderId
    // ---------------------------------------------------------------

    @Test
    void shouldReturnPaymentResponseWhenFound() {
        PaymentTransaction tx = existingTransaction(PaymentStatus.AUTHORIZED);

        given(paymentTransactionRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Optional.of(tx));

        PaymentResponse response = paymentService.getPaymentByOrderId(ORDER_ID, TENANT_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo("AUTHORIZED");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldThrowPaymentNotFoundWhenAbsent() {
        given(paymentTransactionRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(ORDER_ID, TENANT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(ORDER_ID_STR);
    }

    // ---------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------

    private EventEnvelope inventoryReservedEvent(double totalAmount) {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.INVENTORY_RESERVED,
                TENANT_ID,
                Instant.now(),
                Map.of(
                        "orderId", ORDER_ID_STR,
                        "totalAmount", totalAmount
                )
        );
    }

    private WebhookPayload webhookPayload(String status) {
        WebhookPayload payload = new WebhookPayload();
        payload.setEventType("payment_intent.succeeded");
        payload.setOrderId(ORDER_ID_STR);
        payload.setTenantId(TENANT_ID);
        payload.setStatus(status);
        payload.setAmount(new BigDecimal("100.00"));
        return payload;
    }

    private PaymentTransaction existingTransaction(PaymentStatus status) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .orderId(ORDER_ID)
                .amount(new BigDecimal("100.00"))
                .status(status)
                .stripePaymentIntentId("pi_existing")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }
}

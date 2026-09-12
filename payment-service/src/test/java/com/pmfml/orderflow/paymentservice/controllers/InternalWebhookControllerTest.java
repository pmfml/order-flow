package com.pmfml.orderflow.paymentservice.controllers;

import com.pmfml.orderflow.paymentservice.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalWebhookController.class)
class InternalWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final String WEBHOOK_URL = "/internal/v1/payment-webhook";
    private static final String VALID_API_KEY = "dev-secret-key";

    @Test
    void shouldAcceptWebhookWithValidApiKey() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("X-Internal-Api-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayloadJson()))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook(any(WebhookPayload.class));
    }

    @Test
    void shouldRejectWhenApiKeyMissing() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayloadJson()))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).handleWebhook(any());
    }

    @Test
    void shouldRejectWhenApiKeyInvalid() throws Exception {
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayloadJson()))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).handleWebhook(any());
    }

    @Test
    void shouldReturn500WhenServiceThrows() throws Exception {
        doThrow(new RuntimeException("gateway timeout"))
                .when(paymentService).handleWebhook(any(WebhookPayload.class));

        mockMvc.perform(post(WEBHOOK_URL)
                        .header("X-Internal-Api-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayloadJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn400WhenPayloadInvalid() throws Exception {
        String invalidPayload = """
                {
                    "eventType": "payment_intent.succeeded",
                    "orderId": "",
                    "tenantId": "tenant-1",
                    "status": "CAPTURED",
                    "amount": 100.00
                }
                """;

        mockMvc.perform(post(WEBHOOK_URL)
                        .header("X-Internal-Api-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).handleWebhook(any());
    }

    private String validPayloadJson() {
        return """
                {
                    "eventType": "payment_intent.succeeded",
                    "externalReference": "pi_12345",
                    "orderId": "a387b12e-9bef-450d-b20a-ef370c07aa57",
                    "tenantId": "tenant-1",
                    "status": "CAPTURED",
                    "amount": 100.00
                }
                """;
    }
}

package com.pmfml.orderflow.paymentservice.controllers;

import com.pmfml.orderflow.paymentservice.dtos.PaymentResponse;
import com.pmfml.orderflow.paymentservice.exceptions.PaymentNotFoundException;
import com.pmfml.orderflow.paymentservice.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldReturnPaymentWhenExists() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentResponse response = new PaymentResponse(
                paymentId, orderId, new BigDecimal("100.00"), "AUTHORIZED",
                "pi_12345", Instant.now(), Instant.now()
        );

        given(paymentService.getPaymentByOrderId(orderId, "tenant-1"))
                .willReturn(response);

        mockMvc.perform(get("/v1/payments/" + orderId)
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();

        given(paymentService.getPaymentByOrderId(orderId, "tenant-1"))
                .willThrow(new PaymentNotFoundException(orderId));

        mockMvc.perform(get("/v1/payments/" + orderId)
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment Not Found"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void shouldFailWhenTenantHeaderMissing() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/v1/payments/" + orderId))
                .andExpect(status().isBadRequest());
    }
}

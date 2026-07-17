package com.pmfml.orderflow.orderservice.controllers;

import tools.jackson.databind.ObjectMapper;
import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderItemRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // In Spring Boot 3.4+, @MockBean is deprecated in favor of @MockitoBean
    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2)
        ));

        OrderResponse response = new OrderResponse(
                UUID.randomUUID(),
                "tenant-1",
                OrderStatus.PENDING,
                new BigDecimal("200.00"),
                List.of(),
                Instant.now(),
                Instant.now()
        );

        given(orderService.createOrder(any(CreateOrderRequest.class), eq("tenant-1")))
                .willReturn(response);

        mockMvc.perform(post("/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("tenant-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldFailWhenTenantIdIsMissing() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2)
        ));

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailValidationWhenItemsEmpty() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of());

        mockMvc.perform(post("/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }
}

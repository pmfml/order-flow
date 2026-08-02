package com.pmfml.orderflow.orderservice.controllers;

import tools.jackson.databind.ObjectMapper;
import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderItemRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.exceptions.InsufficientStockException;
import com.pmfml.orderflow.orderservice.exceptions.InventoryUnavailableException;
import com.pmfml.orderflow.orderservice.exceptions.ProductNotFoundException;
import com.pmfml.orderflow.orderservice.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    void shouldReturnNotFoundWhenProductIsUnknownToTheTenant() throws Exception {
        givenOrderServiceThrows(new ProductNotFoundException("prod-missing", null));

        mockMvc.perform(validRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product Not Found"))
                .andExpect(jsonPath("$.type").value("https://orderflow.invalid/product-not-found"))
                .andExpect(jsonPath("$.productId").value("prod-missing"));
    }

    /**
     * 409, not 500: the order is well formed, it conflicts with current stock.
     * This previously surfaced as an opaque Internal Server Error.
     */
    @Test
    void shouldReturnConflictWhenStockIsInsufficient() throws Exception {
        givenOrderServiceThrows(new InsufficientStockException("prod-scarce", 500, 1));

        mockMvc.perform(validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient Stock"))
                .andExpect(jsonPath("$.type").value("https://orderflow.invalid/insufficient-stock"))
                .andExpect(jsonPath("$.productId").value("prod-scarce"))
                .andExpect(jsonPath("$.requestedQuantity").value(500))
                .andExpect(jsonPath("$.availableQuantity").value(1));
    }

    /**
     * 503 signals the attempt is worth repeating, which a 500 does not.
     */
    @Test
    void shouldReturnServiceUnavailableWhenInventoryCannotBeReached() throws Exception {
        givenOrderServiceThrows(
                new InventoryUnavailableException("inventory down", new RuntimeException("connection refused")));

        mockMvc.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service Unavailable"));
    }

    @Test
    void shouldReturnInternalServerErrorWithoutLeakingDetailsForUnexpectedFailures() throws Exception {
        givenOrderServiceThrows(new IllegalStateException("connection string user=admin password=hunter2"));

        mockMvc.perform(validRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                // The internal message must not reach the client
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password"))));
    }

    /**
     * The catch-all handler used to convert Spring's own 404 into a 500.
     */
    @Test
    void shouldReturnNotFoundForAnUnknownPathRatherThanServerError() throws Exception {
        mockMvc.perform(post("/v1/does-not-exist")
                .header("X-Tenant-Id", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Malformed JSON is a client error. The catch-all handler used to report it as 500.
     */
    @Test
    void shouldReturnBadRequestForMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [ this is not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnMethodNotAllowedForAnUnsupportedMethod() throws Exception {
        mockMvc.perform(delete("/v1/orders")
                .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isMethodNotAllowed());
    }

    private void givenOrderServiceThrows(RuntimeException exception) {
        given(orderService.createOrder(any(CreateOrderRequest.class), eq("tenant-1")))
                .willThrow(exception);
    }

    private MockHttpServletRequestBuilder validRequest() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2)
        ));
        return post("/v1/orders")
                .header("X-Tenant-Id", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}

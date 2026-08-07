package com.pmfml.orderflow.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "logging.level.org.springframework.cloud.gateway=TRACE"
    })
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class RoutingIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUpAll() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + wireMockServer.port();
        registry.add("ORDER_SERVICE_URL", () -> url);
        registry.add("INVENTORY_SERVICE_URL", () -> url);
        registry.add("PAYMENT_SERVICE_URL", () -> url);
    }

    @Test
    void shouldRouteToOrderServiceAndStripPrefix() {
        // Arrange
        stubFor(post(urlEqualTo("/v1/orders/123"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"123\"}")));

        // Act & Assert
        webClient.post()
                .uri("/api/v1/orders/123")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("123");
    }

    @Test
    void shouldRouteToInventoryServiceAndStripPrefix() {
        // Arrange
        stubFor(get(urlEqualTo("/v1/products/prod-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\": \"prod-1\"}")));

        // Act & Assert
        webClient.get()
                .uri("/api/v1/products/prod-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sku").isEqualTo("prod-1");
    }

    @Test
    void shouldRouteToPaymentServiceAndStripPrefix() {
        // Arrange
        stubFor(get(urlEqualTo("/v1/payments/ord-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"AUTHORIZED\"}")));

        // Act & Assert
        webClient.get()
                .uri("/api/v1/payments/ord-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHORIZED");
    }

    @Test
    void shouldReturn404ForUnknownRoutes() {
        // Act & Assert
        webClient.get()
                .uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound();
    }
}

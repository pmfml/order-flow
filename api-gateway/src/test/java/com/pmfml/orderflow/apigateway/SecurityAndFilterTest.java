package com.pmfml.orderflow.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAndFilterTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private WebTestClient webClient;
    private static WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        // We bind to application context to use the SecurityMockServerConfigurers for mocking JWT
        webClient = WebTestClient.bindToApplicationContext(context)
                .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                .build();
    }

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
        registry.add("JWT_ISSUER_URI", () -> "http://localhost:9999"); // Fake issuer for tests
    }

    @Test
    void shouldRejectRequestWithoutToken() {
        webClient.post()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAcceptRequestWithValidTokenAndInjectHeaders() {
        // Arrange
        stubFor(post(urlEqualTo("/v1/orders"))
                .withHeader("X-Tenant-Id", equalTo("tenant-123"))
                .withHeader("X-User-Id", equalTo("user-456"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"order-1\"}")));

        // Act & Assert
        webClient
                .mutateWith(mockJwt().jwt(jwt -> jwt
                        .claim("tenant_id", "tenant-123")
                        .subject("user-456")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))))
                .post()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("order-1");
    }
}

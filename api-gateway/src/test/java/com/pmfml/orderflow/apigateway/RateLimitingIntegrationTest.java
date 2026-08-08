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
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class RateLimitingIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine")
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private WebTestClient webClient;
    private static WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .baseUrl("http://localhost:" + port)
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
        registry.add("JWT_ISSUER_URI", () -> "http://localhost:9999");
        registry.add("JWT_JWK_SET_URI", () -> "http://localhost:9999/jwks");
        registry.add("REDIS_HOST", redis::getHost);
        registry.add("REDIS_PORT", redis::getFirstMappedPort);
        // Set very low limits for testing
        registry.add("RATE_LIMIT_REPLENISH", () -> 1);
        registry.add("RATE_LIMIT_BURST", () -> 2);
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() {
        // Arrange
        stubFor(get(urlEqualTo("/v1/products/prod-rate-limit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\": \"prod-rate-limit\"}")));

        // Act & Assert
        // Burst capacity is 2, so the first 2 requests should pass (200 OK)
        IntStream.range(0, 2).forEach(i -> {
            webClient
                    .mutateWith(mockJwt().jwt(jwt -> jwt
                            .claim("tenant_id", "tenant-rate-limit")
                            .subject("user-456")
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(3600))))
                    .get()
                    .uri("/api/v1/products/prod-rate-limit")
                    .exchange()
                    .expectStatus().isOk();
        });

        // The 3rd request should hit the rate limit (429 TOO MANY REQUESTS)
        webClient
                .mutateWith(mockJwt().jwt(jwt -> jwt
                        .claim("tenant_id", "tenant-rate-limit")
                        .subject("user-456")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))))
                .get()
                .uri("/api/v1/products/prod-rate-limit")
                .exchange()
                .expectStatus().isEqualTo(429);
    }
}

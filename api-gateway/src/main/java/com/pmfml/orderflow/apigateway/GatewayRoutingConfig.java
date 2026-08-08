package com.pmfml.orderflow.apigateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;

@Configuration
public class GatewayRoutingConfig {

    @Value("${ORDER_SERVICE_URL:http://localhost:8091}")
    private String orderServiceUrl;

    @Value("${INVENTORY_SERVICE_URL:http://localhost:8092}")
    private String inventoryServiceUrl;

    @Value("${PAYMENT_SERVICE_URL:http://localhost:8093}")
    private String paymentServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, KeyResolver tenantKeyResolver, RateLimiter customRedisRateLimiter) {
        return builder.routes()
                .route("order-service", r -> r.path("/api/v1/orders/**", "/api/v1/orders")
                        .filters(f -> f.stripPrefix(1)
                                .requestRateLimiter(c -> c.setRateLimiter(customRedisRateLimiter).setKeyResolver(tenantKeyResolver)))
                        .uri(orderServiceUrl))
                .route("inventory-service", r -> r.path("/api/v1/products/**", "/api/v1/products")
                        .filters(f -> f.stripPrefix(1)
                                .requestRateLimiter(c -> c.setRateLimiter(customRedisRateLimiter).setKeyResolver(tenantKeyResolver)))
                        .uri(inventoryServiceUrl))
                .route("payment-service", r -> r.path("/api/v1/payments/**", "/api/v1/payments")
                        .filters(f -> f.stripPrefix(1)
                                .requestRateLimiter(c -> c.setRateLimiter(customRedisRateLimiter).setKeyResolver(tenantKeyResolver)))
                        .uri(paymentServiceUrl))
                .build();
    }
}

package com.pmfml.orderflow.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Primary;

@Configuration
public class RateLimitingConfig {

    public static final String DEFAULT_RATE_LIMIT_KEY = "anonymous";

    @Value("${app.rate-limit.replenish-rate:10}")
    private int replenishRate;

    @Value("${app.rate-limit.burst-capacity:20}")
    private int burstCapacity;

    @Bean
    @Primary
    public RateLimiter customRedisRateLimiter() {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .filter(authentication -> authentication instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    String tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
                    return tenantId != null ? tenantId : DEFAULT_RATE_LIMIT_KEY;
                })
                .defaultIfEmpty(DEFAULT_RATE_LIMIT_KEY);
    }
}

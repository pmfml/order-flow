package com.pmfml.orderflow.apigateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String USER_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .filter(authentication -> authentication instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    String tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
                    String userId = jwtAuth.getToken().getSubject();

                    ServerWebExchange mutatedExchange = exchange;
                    
                    if (tenantId != null) {
                        mutatedExchange = mutatedExchange.mutate()
                                .request(r -> r.header(TENANT_HEADER, tenantId))
                                .build();
                    }
                    
                    if (userId != null) {
                        mutatedExchange = mutatedExchange.mutate()
                                .request(r -> r.header(USER_HEADER, userId))
                                .build();
                    }

                    return mutatedExchange;
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return 0; // High precedence, but after Spring Security finishes
    }
}

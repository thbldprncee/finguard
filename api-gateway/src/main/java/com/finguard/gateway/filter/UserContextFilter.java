package com.finguard.gateway.filter;

import com.finguard.gateway.config.GatewayProperties;
import com.finguard.gateway.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

/**
 * Forwards identity and client IP to the services. Values a client sends in these headers
 * are always removed first, so they can only come from the gateway.
 */
@Component
@RequiredArgsConstructor
public class UserContextFilter implements GlobalFilter, Ordered {

    static final String CLIENT_IP = "X-Client-Ip";
    static final List<String> GATEWAY_HEADERS = List.of("X-User-Id", "X-User-Role", "X-User-Email", CLIENT_IP);

    private final GatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = clientIp(exchange.getRequest());
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth.getPrincipal() instanceof AuthenticatedUser)
                .map(auth -> Optional.of((AuthenticatedUser) auth.getPrincipal()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> chain.filter(RequestHeaders.modify(exchange, h -> {
                    GATEWAY_HEADERS.forEach(h::remove);
                    h.set(CLIENT_IP, clientIp);
                    user.ifPresent(u -> {
                        h.set("X-User-Id", u.userId().toString());
                        h.set("X-User-Role", u.role());
                    });
                })));
    }

    private String clientIp(ServerHttpRequest request) {
        if (properties.trustProxyHeaders()) {
            String realIp = request.getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

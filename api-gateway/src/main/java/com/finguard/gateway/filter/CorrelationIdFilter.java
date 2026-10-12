package com.finguard.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A client-supplied correlation id is kept only if it looks like one; anything else
 * (log-injection payloads, megabyte-long strings) is replaced by a fresh UUID.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        String correlationId = incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set(CORRELATION_HEADER, correlationId);
        return chain.filter(RequestHeaders.modify(exchange, h -> h.set(CORRELATION_HEADER, correlationId)));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}

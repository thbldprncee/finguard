package com.finguard.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Consumer;

final class RequestHeaders {

    private RequestHeaders() {}

    /**
     * Returns the exchange with modified request headers. Incoming headers are read-only here,
     * and request.mutate().headers(..) still writes into them (UnsupportedOperationException),
     * so a writable copy is exposed through a decorator.
     */
    static ServerWebExchange modify(ServerWebExchange exchange, Consumer<HttpHeaders> change) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(exchange.getRequest().getHeaders());
        change.accept(headers);
        ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
        return exchange.mutate().request(request).build();
    }
}

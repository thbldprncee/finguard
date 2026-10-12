package com.finguard.gateway.filter;

import com.finguard.gateway.config.GatewayProperties;
import com.finguard.gateway.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real GlobalFilters on a request whose headers are read-only, as in production. */
class GatewayFiltersTest {

    private final AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange.getRequest().getHeaders());
        return Mono.empty();
    };

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.remoteAddress(new InetSocketAddress("10.1.2.3", 5555)));
    }

    @Test
    void userContext_stripsSpoofedHeaders_andSetsIdentityAndIp() {
        UUID userId = UUID.randomUUID();
        var filter = new UserContextFilter(new GatewayProperties(true, Duration.ofSeconds(15)));
        ServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/transactions")
                .header("X-User-Id", "attacker").header("X-User-Role", "ADMIN")
                .header("X-Client-Ip", "6.6.6.6").header("X-Real-IP", "203.0.113.9"));
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "u@e.com", "USER"), null, List.of());

        filter.filter(ex, chain).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block();

        HttpHeaders h = forwarded.get();
        assertThat(h.get("X-User-Id")).containsExactly(userId.toString());
        assertThat(h.get("X-User-Role")).containsExactly("USER");
        assertThat(h.get("X-Client-Ip")).containsExactly("203.0.113.9");
    }

    @Test
    void userContext_withoutProxyTrust_usesConnectionAddress() {
        var filter = new UserContextFilter(new GatewayProperties(false, Duration.ofSeconds(15)));
        filter.filter(exchange(MockServerHttpRequest.get("/x").header("X-Real-IP", "6.6.6.6")), chain).block();

        assertThat(forwarded.get().get("X-Client-Ip")).containsExactly("10.1.2.3");
        assertThat(forwarded.get().containsKey("X-User-Id")).isFalse();
    }

    @Test
    void correlationId_keepsValidValue_replacesInvalidOne() {
        var filter = new CorrelationIdFilter();

        filter.filter(exchange(MockServerHttpRequest.get("/x").header("X-Correlation-Id", "abc-12345678")), chain).block();
        assertThat(forwarded.get().getFirst("X-Correlation-Id")).isEqualTo("abc-12345678");

        var ex = exchange(MockServerHttpRequest.get("/x").header("X-Correlation-Id", "bad\nvalue"));
        filter.filter(ex, chain).block();
        String replaced = forwarded.get().getFirst("X-Correlation-Id");
        assertThat(replaced).isNotEqualTo("bad\nvalue").matches("[0-9a-f-]{36}");
        assertThat(ex.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo(replaced);
    }
}

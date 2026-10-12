package com.finguard.gateway.sse;

import com.finguard.gateway.config.GatewayProperties;
import com.finguard.gateway.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = SseController.class)
@Import({SecurityConfig.class, SseSecurityTest.Config.class})
class SseSecurityTest {

    @TestConfiguration
    @EnableConfigurationProperties(GatewayProperties.class)
    static class Config {}

    @Autowired WebTestClient client;
    @Autowired SseController controller;
    @MockBean ReactiveJwtDecoder jwtDecoder;

    private void token(String value, UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue(value).header("alg", "RS256")
                .subject(userId.toString())
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(value)).thenReturn(Mono.just(jwt));
    }

    @Test
    void sseFeed_requiresAuthentication() {
        client.get().uri("/api/sse/events").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void user_receivesOnlyOwnEvents_withoutRuleDetails() {
        UUID alice = UUID.randomUUID();
        token("alice-token", alice, "USER");

        var events = client.get().uri("/api/sse/events")
                .header("Authorization", "Bearer alice-token")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>>() {})
                .getResponseBody()
                .filter(e -> e.data() != null);

        StepVerifier.create(events)
                .thenAwait(Duration.ofMillis(500))
                .then(() -> {
                    controller.publishEvent("fraud.analysis.completed",
                            Map.of("userId", UUID.randomUUID().toString(), "transactionId", "someone-else"));
                    controller.publishEvent("fraud.analysis.completed",
                            Map.of("userId", alice.toString(), "transactionId", "mine",
                                    "decision", "BLOCKED", "triggeredRules", List.of("VELOCITY"), "riskScore", 90));
                })
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("fraud.analysis.completed");
                    assertThat(e.data()).containsEntry("transactionId", "mine").containsEntry("decision", "BLOCKED");
                    assertThat(e.data()).doesNotContainKeys("triggeredRules", "riskScore", "userId");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void feedKeepsWorking_afterAClientDisconnects() {
        UUID bob = UUID.randomUUID();
        token("bob-token", bob, "ANALYST");

        // First client connects and leaves: previously this terminated the shared sink.
        client.get().uri("/api/sse/events").header("Authorization", "Bearer bob-token")
                .accept(MediaType.TEXT_EVENT_STREAM).exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().take(Duration.ofMillis(300)).blockLast();

        var events = client.get().uri("/api/sse/events").header("Authorization", "Bearer bob-token")
                .accept(MediaType.TEXT_EVENT_STREAM).exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>>() {})
                .getResponseBody().filter(e -> e.data() != null);

        StepVerifier.create(events)
                .thenAwait(Duration.ofMillis(500))
                .then(() -> controller.publishEvent("transaction.created", Map.of("userId", "anyone", "transactionId", "t1")))
                .assertNext(e -> assertThat(e.data()).containsEntry("transactionId", "t1"))
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }
}

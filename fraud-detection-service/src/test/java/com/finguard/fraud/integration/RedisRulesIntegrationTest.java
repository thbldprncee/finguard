package com.finguard.fraud.integration;

import com.finguard.fraud.config.FraudProperties;
import com.finguard.fraud.rule.RuleContext;
import com.finguard.fraud.rule.RuleResult;
import com.finguard.fraud.rule.TestFraudProperties;
import com.finguard.fraud.rule.impl.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRulesIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    private final FraudProperties props = TestFraudProperties.with(Map.of(
            "finguard.fraud.rules.velocity.max-transactions", "3",
            "finguard.fraud.rules.velocity.window", "10m",
            "finguard.fraud.rules.structuring.min-transactions", "3"));

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    private RuleContext tx(UUID userId, UUID txId, Instant at, String amount, String country, String device) {
        return RuleContext.builder().transactionId(txId).userId(userId)
                .amount(new BigDecimal(amount)).currency("USD").country(country)
                .deviceId(device).occurredAt(at).build();
    }

    @Test
    void velocity_isSlidingWindow_andCountsRedeliveryOnce() {
        var rule = new VelocityRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T12:00:00Z");

        for (int i = 0; i < 3; i++) {
            assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(i * 60L), "1", "US", null)).triggered()).isFalse();
        }
        UUID fourth = UUID.randomUUID();
        RuleContext fourthTx = tx(user, fourth, t0.plusSeconds(180), "1", "US", null);
        assertThat(rule.evaluate(fourthTx).triggered()).isTrue();
        // Kafka re-delivers the 4th event: still 4 transactions, not 5.
        assertThat(rule.evaluate(fourthTx).reason()).startsWith("4 transactions");

        // 12 minutes later the first ones have left the window (a fixed window would reset abruptly instead).
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0.plus(Duration.ofMinutes(12)), "1", "US", null)).triggered()).isFalse();
    }

    @Test
    void newDevice_staysNewOnRedelivery_andBecomesKnownForNextTransaction() {
        var rule = new NewDeviceRule(redis, props);
        UUID user = UUID.randomUUID();
        RuleContext first = tx(user, UUID.randomUUID(), Instant.now(), "1", "US", "device-aaaa1111");

        assertThat(rule.evaluate(first).triggered()).isTrue();
        assertThat(rule.evaluate(first).triggered()).as("re-delivery of the same transaction").isTrue();
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), Instant.now(), "1", "US", "device-aaaa1111")).triggered()).isFalse();
    }

    @Test
    void newDevice_missingDeviceId_isRisky() {
        assertThat(new NewDeviceRule(redis, props).evaluate(tx(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "1", "US", null)))
                .extracting(RuleResult::triggered).isEqualTo(true);
    }

    @Test
    void countryChange_isStableOnRedelivery() {
        var rule = new CountryChangeRule(redis, props);
        UUID user = UUID.randomUUID();
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), Instant.now(), "1", "US", null)).triggered()).isFalse();

        RuleContext fromDe = tx(user, UUID.randomUUID(), Instant.now(), "1", "DE", null);
        assertThat(rule.evaluate(fromDe).triggered()).isTrue();
        assertThat(rule.evaluate(fromDe).triggered()).as("re-delivery compares with US again").isTrue();

        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), Instant.now(), "1", "DE", null)).triggered()).isFalse();
    }

    @Test
    void structuring_detectsSplitPayments() {
        var rule = new StructuringRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T12:00:00Z");

        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0, "4000", "US", null)).triggered()).isFalse();
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(60), "4000", "US", null)).triggered()).isFalse();
        RuleResult third = rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(120), "4000", "US", null));
        assertThat(third.triggered()).isTrue();
        assertThat(third.reason()).contains("12000");
    }

    @Test
    void repeatedBlocked_countsRecordedBlocksOnce() {
        var rule = new RepeatedBlockedTransactionsRule(redis, props);
        UUID user = UUID.randomUUID();
        UUID blockedTx = UUID.randomUUID();
        Instant t0 = Instant.now();

        rule.recordBlocked(user, blockedTx, t0);
        rule.recordBlocked(user, blockedTx, t0);

        RuleResult next = rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(60), "1", "US", null));
        assertThat(next.triggered()).isTrue();
        assertThat(next.reason()).startsWith("1 blocked");
    }
}

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
    void countryChange_comparesWithPredecessorByCreationTime_notByProcessingOrder() {
        var rule = new CountryChangeRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        RuleContext usAt1000 = tx(user, UUID.randomUUID(), t0, "1", "US", null);
        RuleContext deAt1001 = tx(user, UUID.randomUUID(), t0.plusSeconds(60), "1", "DE", null);

        // The 10:01 transaction is processed first (the 10:00 one was waiting for a retry).
        assertThat(rule.evaluate(deAt1001).triggered()).as("no history yet").isFalse();
        assertThat(rule.evaluate(usAt1000).triggered()).as("10:00 has no predecessor").isFalse();
        // Re-delivery of 10:01 now sees its real predecessor (US at 10:00).
        assertThat(rule.evaluate(deAt1001).reason()).isEqualTo("Country changed from US to DE");
    }

    @Test
    void newDevice_firstTransactionByCreationTime_isTheNewOne_evenIfProcessedLater() {
        var rule = new NewDeviceRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        RuleContext earlier = tx(user, UUID.randomUUID(), t0, "1", "US", "device-late0001");
        RuleContext later = tx(user, UUID.randomUUID(), t0.plusSeconds(60), "1", "US", "device-late0001");

        assertThat(rule.evaluate(later).triggered()).as("processed first, earliest seen so far").isTrue();
        assertThat(rule.evaluate(earlier).triggered()).as("really the first transaction from the device").isTrue();
        assertThat(rule.evaluate(later).triggered()).as("re-delivery: no longer the earliest").isFalse();
    }

    @Test
    void velocity_lateEventStillCountsItsWindow() {
        var rule = new VelocityRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T12:00:00Z");
        for (int i = 1; i <= 3; i++) {
            rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(i), "1", "US", null));
        }
        // A much newer event must not purge entries a late event still needs.
        rule.evaluate(tx(user, UUID.randomUUID(), t0.plus(Duration.ofMinutes(30)), "1", "US", null));

        RuleResult late = rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(4), "1", "US", null));
        assertThat(late.triggered()).isTrue();
        assertThat(late.reason()).startsWith("4 transactions");
    }

    @Test
    void structuring_ignoresAmountsAboveTheThreshold() {
        var rule = new StructuringRule(redis, props);
        UUID user = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-05-01T12:00:00Z");

        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0, "50000", "US", null)).triggered())
                .as("a large payment is HIGH_AMOUNT's job").isFalse();
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(60), "100", "US", null)).triggered()).isFalse();
        assertThat(rule.evaluate(tx(user, UUID.randomUUID(), t0.plusSeconds(120), "100", "US", null)).triggered())
                .as("3 transactions, but only 200 below the threshold").isFalse();
    }
}

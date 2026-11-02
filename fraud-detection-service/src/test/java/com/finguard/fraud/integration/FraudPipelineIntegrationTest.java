package com.finguard.fraud.integration;

import com.finguard.fraud.entity.FraudAnalysis;
import com.finguard.fraud.outbox.OutboxEventRepository;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.dto.FraudRuleUpdateRequest;
import com.finguard.fraud.exception.StaleVersionException;
import com.finguard.fraud.service.FraudAnalysisService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class FraudPipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("fraud_db");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.security.protocol", () -> "PLAINTEXT");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("finguard.security.oidc.issuer", () -> "http://localhost/auth/realms/finguard");
        registry.add("finguard.security.oidc.jwk-set-uri", () -> "http://localhost/auth/realms/finguard/protocol/openid-connect/certs");
        registry.add("finguard.outbox.poll-interval", () -> "100ms");
        registry.add("finguard.fraud.rule-config-refresh-interval", () -> "0s");
    }

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired FraudAnalysisRepository analysisRepository;
    @Autowired FraudRuleRepository ruleRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired FraudAnalysisService fraudAnalysisService;

    private Map<String, Object> event(UUID txId, UUID userId, String amount, String category) {
        Map<String, Object> e = new HashMap<>();
        e.put("transactionId", txId.toString());
        e.put("userId", userId.toString());
        e.put("amount", amount);
        e.put("currency", "USD");
        e.put("country", "US");
        e.put("merchantCategory", category);
        e.put("paymentMethod", "CARD");
        e.put("deviceId", "device-pipeline-1");
        e.put("occurredAt", Instant.parse("2026-05-01T12:00:00Z").toString());
        return e;
    }

    private void send(UUID userId, Map<String, Object> payload) {
        var record = new ProducerRecord<String, Object>("transaction.created", userId.toString(), payload);
        record.headers().add("X-Correlation-Id", "corr-pipeline".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);
    }

    @Test
    void transactionIsAnalysedOnce_evenWhenDeliveredTwice_andResultGoesThroughOutbox() {
        UUID txId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> payload = event(txId, userId, "15000", "GAMBLING");

        send(userId, payload);
        send(userId, payload);

        await().atMost(Duration.ofSeconds(30)).until(() -> analysisRepository.findByTransactionId(txId).isPresent());
        FraudAnalysis analysis = analysisRepository.findByTransactionId(txId).orElseThrow();
        // HIGH_AMOUNT 30 + SUSPICIOUS_MERCHANT 20 + NEW_DEVICE 15 = 65 (weights from fraud_rules)
        assertThat(analysis.getRiskScore()).isEqualTo(65);
        assertThat(analysis.getDecision()).isEqualTo(FraudAnalysis.Decision.REVIEW_REQUIRED);
        assertThat(analysis.getUserId()).isEqualTo(userId);

        await().atMost(Duration.ofSeconds(30)).until(() -> outboxRepository.findAll().stream()
                .anyMatch(e -> e.getAggregateId().equals(txId.toString()) && e.getPublishedAt() != null));
        assertThat(outboxRepository.findAll().stream().filter(e -> e.getAggregateId().equals(txId.toString())))
                .singleElement()
                .satisfies(e -> assertThat(e.getCorrelationId()).isEqualTo("corr-pipeline"));
    }

    @Test
    void disablingARuleInTheDatabase_changesTheScore() {
        var rule = ruleRepository.findAll().stream().filter(r -> r.getCode().equals("SUSPICIOUS_MERCHANT")).findFirst().orElseThrow();
        rule.setEnabled(false);
        rule = ruleRepository.save(rule); // keep the returned copy: it carries the new @Version
        try {
            UUID txId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            send(userId, event(txId, userId, "15000", "GAMBLING"));

            await().atMost(Duration.ofSeconds(30)).until(() -> analysisRepository.findByTransactionId(txId).isPresent());
            assertThat(analysisRepository.findByTransactionId(txId).orElseThrow().getRiskScore()).isEqualTo(45);
        } finally {
            rule.setEnabled(true);
            ruleRepository.save(rule);
        }
    }

    @Test
    void blockedHistoryComesFromPostgres_soTheNextTransactionSeesIt() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        send(userId, event(first, userId, "15000", "GAMBLING"));
        await().atMost(Duration.ofSeconds(30)).until(() -> analysisRepository.findByTransactionId(first).isPresent());
        FraudAnalysis firstAnalysis = analysisRepository.findByTransactionId(first).orElseThrow();
        firstAnalysis.setDecision(FraudAnalysis.Decision.BLOCKED); // force a block for the history check
        analysisRepository.save(firstAnalysis);

        Map<String, Object> next = event(second, userId, "10", "RETAIL");
        next.put("occurredAt", Instant.parse("2026-05-01T13:00:00Z").toString());
        send(userId, next);
        await().atMost(Duration.ofSeconds(30)).until(() -> analysisRepository.findByTransactionId(second).isPresent());
        assertThat(analysisRepository.findByTransactionId(second).orElseThrow().getTriggeredRules()).contains("REPEATED_BLOCKED");
    }

    @Test
    void malformedEvent_endsInReviewViaDeadLetter_insteadOfStayingPending() {
        UUID txId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> broken = event(txId, userId, "not-a-number", "RETAIL");

        send(userId, broken);

        await().atMost(Duration.ofSeconds(30)).until(() -> analysisRepository.findByTransactionId(txId).isPresent());
        FraudAnalysis analysis = analysisRepository.findByTransactionId(txId).orElseThrow();
        assertThat(analysis.getDecision()).isEqualTo(FraudAnalysis.Decision.REVIEW_REQUIRED);
        assertThat(analysis.getFailedRules()).containsExactly("ENGINE");
    }

    /**
     * Two admins edited the same version of a rule and press Save at the same moment.
     * Exactly one change may win; the other must fail instead of silently overwriting it.
     */
    @Test
    void concurrentRuleUpdates_withTheSameVersion_onlyOneWins() throws Exception {
        var rule = ruleRepository.findAll().stream().filter(r -> r.getCode().equals("NIGHT_ACTIVITY")).findFirst().orElseThrow();
        long version = rule.getVersion();
        int originalWeight = rule.getWeight();
        boolean originalEnabled = rule.isEnabled();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> disable = () -> {
                start.await();
                return fraudAnalysisService.updateRule(rule.getId(), version, new FraudRuleUpdateRequest(false, null), UUID.randomUUID(), null);
            };
            Callable<Object> reweight = () -> {
                start.await();
                return fraudAnalysisService.updateRule(rule.getId(), version, new FraudRuleUpdateRequest(null, 50), UUID.randomUUID(), null);
            };
            Future<Object> a = pool.submit(disable);
            Future<Object> b = pool.submit(reweight);
            start.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<Object> f : List.of(a, b)) {
                try {
                    f.get();
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfAny(StaleVersionException.class, ObjectOptimisticLockingFailureException.class);
                    rejected++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);

            var stored = ruleRepository.findById(rule.getId()).orElseThrow();
            assertThat(stored.getVersion()).isEqualTo(version + 1);
            // Exactly one of the two changes is visible, never a mix that loses the other silently.
            boolean disabledWon = !stored.isEnabled() && stored.getWeight() == originalWeight;
            boolean reweightWon = stored.isEnabled() == originalEnabled && stored.getWeight() == 50;
            assertThat(disabledWon ^ reweightWon).isTrue();
        } finally {
            pool.shutdownNow();
            var current = ruleRepository.findById(rule.getId()).orElseThrow();
            current.setEnabled(originalEnabled);
            current.setWeight(originalWeight);
            ruleRepository.save(current);
        }
    }
}

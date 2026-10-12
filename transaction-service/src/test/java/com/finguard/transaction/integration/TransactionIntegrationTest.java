package com.finguard.transaction.integration;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.entity.Transaction;
import com.finguard.transaction.exception.IdempotencyKeyReuseException;
import com.finguard.transaction.outbox.OutboxEventRepository;
import com.finguard.transaction.repository.TransactionRepository;
import com.finguard.transaction.security.RequestMetadata;
import com.finguard.transaction.service.AccountService;
import com.finguard.transaction.service.TransactionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transaction_db");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.security.protocol", () -> "PLAINTEXT");
        registry.add("finguard.security.oidc.issuer", () -> "http://localhost/auth/realms/finguard");
        registry.add("finguard.security.oidc.jwk-set-uri", () -> "http://localhost/auth/realms/finguard/protocol/openid-connect/certs");
        registry.add("finguard.outbox.poll-interval", () -> "100ms");
    }

    @Autowired TransactionService transactionService;
    @Autowired AccountService accountService;
    @Autowired TransactionRepository transactionRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired TransactionTemplate tx;

    private CreateTransactionRequest request(String account, String amount) {
        return new CreateTransactionRequest(account, "ACC-EXTERNAL-1", new BigDecimal(amount), "USD", "US", "RETAIL", "CARD");
    }

    private RequestMetadata meta(String key) {
        return new RequestMetadata(key, UUID.randomUUID().toString(), "198.51.100.1", "device-abcdef12");
    }

    @Test
    void concurrentRequestsWithSameKey_createExactlyOneTransactionAndOneEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        String account = accountService.open(userId, "USD").accountNumber();
        String key = "concurrent-" + UUID.randomUUID();
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransactionService.CreateResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return transactionService.createTransaction(request(account, "42.00"), userId, meta(key));
            }));
        }
        start.countDown();

        Set<UUID> ids = new HashSet<>();
        int replayed = 0;
        for (var f : futures) {
            var result = f.get(30, TimeUnit.SECONDS);
            ids.add(result.transaction().id());
            if (result.replayed()) replayed++;
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(replayed).isEqualTo(threads - 1);
        assertThat(transactionRepository.findByUserId(userId, org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
        long events = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(ids.iterator().next().toString()))
                .count();
        assertThat(events).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentPayload_isRejected_andSameKeyOtherUser_isIndependent() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String aliceAccount = accountService.open(alice, "USD").accountNumber();
        String bobAccount = accountService.open(bob, "USD").accountNumber();
        String key = "shared-" + UUID.randomUUID();

        var first = transactionService.createTransaction(request(aliceAccount, "10"), alice, meta(key));

        assertThatThrownBy(() -> transactionService.createTransaction(request(aliceAccount, "9999"), alice, meta(key)))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        var bobs = transactionService.createTransaction(request(bobAccount, "10"), bob, meta(key));
        assertThat(bobs.replayed()).isFalse();
        assertThat(bobs.transaction().id()).isNotEqualTo(first.transaction().id());
        assertThat(bobs.transaction().userId()).isEqualTo(bob);
    }

    @Test
    void outboxRelay_publishesEventToKafka_keyedByUser_withEventHeaders() {
        UUID userId = UUID.randomUUID();
        String account = accountService.open(userId, "USD").accountNumber();
        var created = transactionService.createTransaction(request(account, "77.50"), userId, meta(null));
        String txId = created.transaction().id().toString();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerRecord<String, String> found = null;
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of("transaction.created"));
            Instant deadline = Instant.now().plusSeconds(30);
            while (found == null && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(txId)) {
                        found = record;
                    }
                }
            }
        }

        assertThat(found).as("transaction.created for %s", txId).isNotNull();
        assertThat(found.key()).isEqualTo(userId.toString());
        assertThat(found.value()).contains("\"amount\":\"77.50\"").contains("\"eventType\":\"TRANSACTION_CREATED\"");
        assertThat(new String(found.headers().lastHeader("X-Event-Id").value(), StandardCharsets.UTF_8)).isNotBlank();
        assertThat(outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(txId))
                .allMatch(e -> e.getPublishedAt() != null)).isTrue();
    }

    @Test
    void decisionIsAppliedOnlyOnce() {
        UUID userId = UUID.randomUUID();
        String account = accountService.open(userId, "USD").accountNumber();
        UUID txId = transactionService.createTransaction(request(account, "5"), userId, meta(null)).transaction().id();

        assertThat(tx.<Integer>execute(s -> transactionRepository.applyDecision(txId, Transaction.Status.BLOCKED, Instant.now()))).isEqualTo(1);
        assertThat(tx.<Integer>execute(s -> transactionRepository.applyDecision(txId, Transaction.Status.APPROVED, Instant.now()))).isZero();
        assertThat(transactionRepository.findById(txId).orElseThrow().getStatus()).isEqualTo(Transaction.Status.BLOCKED);
    }
}

package com.finguard.transaction.outbox;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes outbox events right after the transaction that wrote them commits, instead of
 * waiting for the next poll (up to pollInterval per hop). The poll stays as the safety net for
 * anything a trigger misses: a crash after commit, a failed send waiting for its retry.
 * <p>
 * Requests are debounced: the first commit schedules a run triggerDelay later, commits in the
 * meantime join it, and the run publishes everything that is due in one database transaction.
 * Without the delay every event got its own small relay transaction; that doubled the commits
 * per event and, on the shared PostgreSQL, cut the fraud pipeline's throughput by about a third
 * (load test 2026-10-29). A few milliseconds of delay buys batches again. Concurrent runs (trigger + poll, several instances)
 * are safe: rows are locked with SKIP LOCKED and a key's later event waits for its earlier one.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "finguard.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxTrigger {

    private final OutboxRelay relay;
    private final long delayMicros;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "outbox-trigger");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public OutboxTrigger(OutboxRelay relay, OutboxProperties properties) {
        this(relay, properties.triggerDelay());
    }

    OutboxTrigger(OutboxRelay relay, Duration delay) {
        this.relay = relay;
        this.delayMicros = delay.toNanos() / 1000;
    }

    public void requestPublish() {
        if (queued.compareAndSet(false, true)) {
            executor.schedule(() -> {
                queued.set(false); // commits from now on queue the next run
                try {
                    relay.publishPending();
                } catch (RuntimeException e) {
                    log.warn("Immediate outbox publish failed, the poll will retry: {}", e.getMessage());
                }
            }, delayMicros, TimeUnit.MICROSECONDS);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

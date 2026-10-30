package com.finguard.transaction.outbox;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxTriggerTest {

    @Test
    void requestsWhileARunIsQueued_coalesceIntoOneRun() throws Exception {
        OutboxRelay relay = mock(OutboxRelay.class);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        doAnswer(inv -> {
            if (runs.incrementAndGet() == 1) {
                running.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(relay).publishPending();

        OutboxTrigger trigger = new OutboxTrigger(relay, java.time.Duration.ofMillis(10));
        trigger.requestPublish();                       // run 1 starts and blocks
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 100; i++) {
            trigger.requestPublish();                   // all of these queue a single run 2
        }
        release.countDown();

        verify(relay, timeout(5000).times(2)).publishPending();
        Thread.sleep(200);
        assertThat(runs.get()).isEqualTo(2);
        trigger.shutdown();
    }

    @Test
    void aFailingRun_doesNotStopLaterRuns() {
        OutboxRelay relay = mock(OutboxRelay.class);
        doThrow(new IllegalStateException("broker down")).doNothing().when(relay).publishPending();
        OutboxTrigger trigger = new OutboxTrigger(relay, java.time.Duration.ofMillis(10));

        trigger.requestPublish();
        verify(relay, timeout(5000).times(1)).publishPending();
        trigger.requestPublish();
        verify(relay, timeout(5000).times(2)).publishPending();
        trigger.shutdown();
    }
}

package com.finguard.fraud.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the head of each message key's queue: the oldest unpublished event of that key, and
     * only if it is due. A later event of the same key is never returned while an earlier one is
     * unpublished (e.g. waiting for a retry), so per-key order in Kafka matches creation order.
     * Different keys do not block each other. SKIP LOCKED lets several instances relay in
     * parallel; another instance cannot jump ahead either, because the NOT EXISTS sees the
     * locked-but-unpublished predecessor.
     */
    @Query(value = """
            SELECT * FROM {h-schema}outbox_events e
            WHERE e.published_at IS NULL
              AND e.next_attempt_at <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM {h-schema}outbox_events p
                  WHERE p.message_key = e.message_key
                    AND p.published_at IS NULL
                    AND (p.created_at, p.id) < (e.created_at, e.id))
            ORDER BY e.created_at, e.id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM {h-schema}outbox_events WHERE published_at < :before", nativeQuery = true)
    int deletePublishedBefore(@Param("before") Instant before);
}

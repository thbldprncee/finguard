package com.finguard.transaction.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events. SKIP LOCKED lets several service instances
     * relay in parallel without publishing the same row twice.
     */
    @Query(value = """
            SELECT * FROM {h-schema}outbox_events
            WHERE published_at IS NULL AND next_attempt_at <= :now
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query(value = "DELETE FROM {h-schema}outbox_events WHERE published_at < :before", nativeQuery = true)
    int deletePublishedBefore(@Param("before") Instant before);
}

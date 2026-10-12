package com.finguard.transaction.repository;

import com.finguard.transaction.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Claims the key atomically. If a concurrent request inserted the same key and has not
     * committed yet, PostgreSQL blocks here until it does, then reports the conflict (0 rows);
     * if that request rolled back, this insert succeeds instead. A "check, then insert" in Java
     * would let both requests pass the check.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}idempotency_keys (id, user_id, idempotency_key, request_hash, created_at)
            VALUES (:id, :userId, :key, :requestHash, :createdAt)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("userId") UUID userId,
                       @Param("key") String key,
                       @Param("requestHash") String requestHash,
                       @Param("createdAt") Instant createdAt);

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Modifying
    @Query("UPDATE IdempotencyKey k SET k.transactionId = :txId WHERE k.userId = :userId AND k.idempotencyKey = :key")
    int attachTransaction(@Param("userId") UUID userId, @Param("key") String key, @Param("txId") UUID transactionId);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}

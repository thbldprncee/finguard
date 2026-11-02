package com.finguard.fraud.repository;

import com.finguard.fraud.entity.FraudAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FraudAnalysisRepository extends JpaRepository<FraudAnalysis, UUID> {

    Optional<FraudAnalysis> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);

    /**
     * Blocked transactions of a user created in [from, to), counted up to {@code limit}:
     * PostgreSQL is the source of truth. The cap keeps the cost independent of the user's history
     * (a full count read every blocked row of a heavily blocked user, for every transaction), and
     * count(*) over the (user_id, decision, transaction_occurred_at) index needs no heap reads.
     */
    @Query(value = """
            SELECT count(*) FROM (
                SELECT 1 FROM fraud.fraud_analyses
                WHERE user_id = :userId AND decision = 'BLOCKED'
                  AND transaction_occurred_at >= :from AND transaction_occurred_at < :to
                LIMIT :limit) capped
            """, nativeQuery = true)
    long countBlockedUpTo(@Param("userId") UUID userId, @Param("from") Instant from,
                          @Param("to") Instant to, @Param("limit") int limit);

    @Query("""
            SELECT fa FROM FraudAnalysis fa
            WHERE fa.decision IN (com.finguard.fraud.entity.FraudAnalysis.Decision.REVIEW_REQUIRED,
                                  com.finguard.fraud.entity.FraudAnalysis.Decision.BLOCKED)
            """)
    Page<FraudAnalysis> findAlerts(Pageable pageable);
}

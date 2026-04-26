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

    Page<FraudAnalysis> findByDecision(FraudAnalysis.Decision decision, Pageable pageable);

    @Query("""
            SELECT COUNT(fa) FROM FraudAnalysis fa
            WHERE fa.decision = 'BLOCKED'
              AND fa.analyzedAt >= :since
              AND fa.transactionId IN :transactionIds
            """)
    long countBlockedByTransactionIdsSince(
            @Param("transactionIds") java.util.Collection<UUID> transactionIds,
            @Param("since") Instant since);

    long countByDecisionAndAnalyzedAtAfter(FraudAnalysis.Decision decision, Instant after);

    @Query("""
            SELECT fa FROM FraudAnalysis fa
            WHERE fa.decision IN ('REVIEW_REQUIRED', 'BLOCKED')
            ORDER BY fa.analyzedAt DESC
            """)
    Page<FraudAnalysis> findAlerts(Pageable pageable);
}

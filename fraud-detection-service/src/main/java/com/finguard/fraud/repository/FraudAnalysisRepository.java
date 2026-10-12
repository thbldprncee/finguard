package com.finguard.fraud.repository;

import com.finguard.fraud.entity.FraudAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FraudAnalysisRepository extends JpaRepository<FraudAnalysis, UUID> {

    Optional<FraudAnalysis> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);

    @Query("""
            SELECT fa FROM FraudAnalysis fa
            WHERE fa.decision IN (com.finguard.fraud.entity.FraudAnalysis.Decision.REVIEW_REQUIRED,
                                  com.finguard.fraud.entity.FraudAnalysis.Decision.BLOCKED)
            """)
    Page<FraudAnalysis> findAlerts(Pageable pageable);
}

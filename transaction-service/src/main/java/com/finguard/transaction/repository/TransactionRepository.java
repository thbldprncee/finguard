package com.finguard.transaction.repository;

import com.finguard.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndStatus(UUID userId, Transaction.Status status, Pageable pageable);

    Optional<Transaction> findByExternalTransactionId(String externalTransactionId);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND t.createdAt >= :since
            """)
    Page<Transaction> findByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}

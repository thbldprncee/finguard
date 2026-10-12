package com.finguard.transaction.repository;

import com.finguard.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    /**
     * PENDING -> final status, at most once. A replayed or forged older decision cannot
     * flip a transaction that has already been decided.
     */
    @Modifying
    @Query("""
            UPDATE Transaction t
            SET t.status = :status, t.version = t.version + 1, t.updatedAt = :now
            WHERE t.id = :id AND t.status = com.finguard.transaction.entity.Transaction.Status.PENDING
            """)
    int applyDecision(@Param("id") UUID id, @Param("status") Transaction.Status status, @Param("now") Instant now);
}

package com.finguard.transaction.repository;

import com.finguard.transaction.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAt(UUID userId);

    Optional<Account> findByAccountNumberAndUserId(String accountNumber, UUID userId);

    boolean existsByAccountNumber(String accountNumber);

    long countByUserId(UUID userId);
}

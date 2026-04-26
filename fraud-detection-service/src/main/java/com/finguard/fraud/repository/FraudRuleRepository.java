package com.finguard.fraud.repository;

import com.finguard.fraud.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    List<FraudRule> findByEnabled(boolean enabled);
}

package com.finguard.fraud.repository;

import com.finguard.fraud.entity.FraudLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudLabelRepository extends JpaRepository<FraudLabel, UUID> {
}

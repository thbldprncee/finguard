package com.finguard.fraud.controller;

import com.finguard.fraud.dto.FraudAnalysisResponse;
import com.finguard.fraud.dto.FraudRuleUpdateRequest;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.service.FraudAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Fraud analysis and rule management")
@SecurityRequirement(name = "bearerAuth")
public class FraudController {

    private final FraudAnalysisService fraudAnalysisService;
    private final FraudRuleRepository fraudRuleRepository;

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Get fraud alerts (REVIEW_REQUIRED and BLOCKED)")
    public Page<FraudAnalysisResponse> getAlerts(
            @PageableDefault(size = 20, sort = "analyzedAt") Pageable pageable) {
        return fraudAnalysisService.getAlerts(pageable);
    }

    @GetMapping("/analysis/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'ANALYST', 'ADMIN')")
    @Operation(summary = "Get fraud analysis for a transaction")
    public FraudAnalysisResponse getAnalysis(@PathVariable UUID transactionId) {
        return fraudAnalysisService.getByTransactionId(transactionId);
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "List all fraud rules")
    public List<FraudRule> getRules() {
        return fraudRuleRepository.findAll();
    }

    @PatchMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update fraud rule (enable/disable, change weight)")
    public FraudRule updateRule(@PathVariable UUID id, @Valid @RequestBody FraudRuleUpdateRequest request) {
        FraudRule rule = fraudRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fraud rule not found: " + id));

        if (request.enabled() != null) rule.setEnabled(request.enabled());
        if (request.weight() != null) rule.setWeight(request.weight());

        return fraudRuleRepository.save(rule);
    }
}

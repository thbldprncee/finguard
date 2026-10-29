package com.finguard.fraud.controller;

import com.finguard.fraud.dto.FraudAnalysisResponse;
import com.finguard.fraud.dto.FraudRuleUpdateRequest;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.exception.InvalidHeaderException;
import com.finguard.fraud.exception.PreconditionRequiredException;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.security.AuthenticatedUser;
import com.finguard.fraud.service.FraudAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * Headers and path variables are validated declaratively (Spring MVC method validation):
 * a violation is a 400 with the offending parameter, handled in GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Fraud analysis and rule management")
@SecurityRequirement(name = "bearerAuth")
public class FraudController {

    /** Same format the gateway enforces (empty = not sent); checked again because services do not trust the network. */
    static final String CORRELATION_ID = "^([A-Za-z0-9-]{8,64})?$";

    /** A strong or weak entity tag holding the rule version: 5, "5" or W/"5". */
    private static final java.util.regex.Pattern ETAG = java.util.regex.Pattern.compile("^(?:W/)?\"?(\\d{1,18})\"?$");

    private final FraudAnalysisService fraudAnalysisService;
    private final FraudRuleRepository fraudRuleRepository;

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Get fraud alerts (REVIEW_REQUIRED and BLOCKED)")
    public Page<FraudAnalysisResponse> getAlerts(
            @PageableDefault(size = 20, sort = "analyzedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return fraudAnalysisService.getAlerts(pageable);
    }

    @GetMapping("/analysis/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'ANALYST', 'ADMIN')")
    @Operation(summary = "Fraud analysis of a transaction (USER: own transactions, decision only)")
    public FraudAnalysisResponse getAnalysis(@PathVariable UUID transactionId,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return fraudAnalysisService.getByTransactionId(transactionId, user);
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "List all fraud rules (each carries its version for If-Match)")
    public List<FraudRule> getRules() {
        return fraudRuleRepository.findAll(Sort.by("code"));
    }

    @GetMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Operation(summary = "Get one fraud rule; the ETag header holds its version")
    public ResponseEntity<FraudRule> getRule(@PathVariable UUID id) {
        FraudRule rule = fraudRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fraud rule not found: " + id));
        return ResponseEntity.ok().eTag(etag(rule)).body(rule);
    }

    @PatchMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update fraud rule (enable/disable, change weight); takes effect immediately",
            description = "Requires If-Match with the rule version the admin edited. "
                    + "412 if the rule was changed meanwhile, 428 if If-Match is missing.")
    public ResponseEntity<FraudRule> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody FraudRuleUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = "Rule version from GET (ETag or the version field), e.g. \"3\"")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Correlation-Id", required = false)
            @Pattern(regexp = CORRELATION_ID, message = "must be 8-64 letters, digits or dashes") String correlationId) {
        long expectedVersion = parseIfMatch(ifMatch);
        FraudRule updated = fraudAnalysisService.updateRule(id, expectedVersion, request, actor.userId(),
                correlationId == null || correlationId.isBlank() ? null : correlationId);
        return ResponseEntity.ok().eTag(etag(updated)).body(updated);
    }

    private static String etag(FraudRule rule) {
        return "\"" + rule.getVersion() + "\"";
    }

    static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "If-Match header with the rule version is required, so concurrent edits are not lost");
        }
        Matcher m = ETAG.matcher(ifMatch.trim());
        if (!m.matches()) {
            throw new InvalidHeaderException("Invalid If-Match header: expected the rule version, e.g. \"3\"");
        }
        return Long.parseLong(m.group(1));
    }
}

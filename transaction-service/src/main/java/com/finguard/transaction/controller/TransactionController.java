package com.finguard.transaction.controller;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.dto.TransactionResponse;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.security.RequestMetadata;
import com.finguard.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Body fields are validated by Bean Validation on {@link CreateTransactionRequest} (@Valid);
 * headers declaratively below (Spring MVC method validation). Either way a violation is a 400
 * that names the offending field, see GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = HeaderPatterns.IDEMPOTENCY_KEY, message = "must be 8-100 letters, digits, '_' or '-'") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
            @Pattern(regexp = HeaderPatterns.CORRELATION_ID, message = "must be 8-64 letters, digits or '-'") String correlationId,
            @RequestHeader(value = "X-Device-Id", required = false)
            @Pattern(regexp = HeaderPatterns.DEVICE_ID, message = "must be 8-100 letters, digits, '_' or '-'") String deviceId,
            @RequestHeader(value = "X-Client-Ip", required = false)
            @Pattern(regexp = HeaderPatterns.CLIENT_IP, message = "must be an IP address") String clientIp,
            HttpServletRequest httpRequest) {
        var meta = new RequestMetadata(
                blankToNull(idempotencyKey),
                blankToNull(correlationId),
                clientIp != null && !clientIp.isBlank() ? clientIp : httpRequest.getRemoteAddr(),
                blankToNull(deviceId));

        var result = transactionService.createTransaction(request, user.userId(), meta);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.transaction());
    }

    @GetMapping
    @Operation(summary = "List transactions (own; all for ANALYST/ADMIN)")
    public Page<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return transactionService.listTransactions(user, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public TransactionResponse getById(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return transactionService.getTransaction(id, user);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

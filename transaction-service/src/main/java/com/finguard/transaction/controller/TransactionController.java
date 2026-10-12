package com.finguard.transaction.controller;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.dto.TransactionResponse;
import com.finguard.transaction.exception.InvalidHeaderException;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.security.RequestMetadata;
import com.finguard.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Client-Ip", required = false) String clientIp,
            HttpServletRequest httpRequest) {
        var meta = new RequestMetadata(
                validHeader("Idempotency-Key", idempotencyKey, IDEMPOTENCY_KEY),
                correlationId,
                clientIp != null && !clientIp.isBlank() ? clientIp : httpRequest.getRemoteAddr(),
                validHeader("X-Device-Id", deviceId, DEVICE_ID));

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

    private static String validHeader(String name, String value, Pattern pattern) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!pattern.matcher(value).matches()) {
            throw new InvalidHeaderException("Invalid " + name + " header");
        }
        return value;
    }
}

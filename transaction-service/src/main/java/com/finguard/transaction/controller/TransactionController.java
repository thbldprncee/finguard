package com.finguard.transaction.controller;

import com.finguard.transaction.dto.CreateTransactionRequest;
import com.finguard.transaction.dto.TransactionResponse;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new transaction")
    public TransactionResponse create(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return transactionService.createTransaction(request, user.userId(), idempotencyKey, correlationId);
    }

    @GetMapping
    @Operation(summary = "List transactions (paginated)")
    public Page<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return transactionService.listTransactions(user.userId(), user.role(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public TransactionResponse getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return transactionService.getTransaction(id, user.userId(), user.role());
    }
}

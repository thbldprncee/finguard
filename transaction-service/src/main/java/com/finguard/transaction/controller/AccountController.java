package com.finguard.transaction.controller;

import com.finguard.transaction.dto.AccountResponse;
import com.finguard.transaction.dto.OpenAccountRequest;
import com.finguard.transaction.security.AuthenticatedUser;
import com.finguard.transaction.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "The caller's own accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "List my accounts")
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.listOwn(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account")
    public AccountResponse open(@Valid @RequestBody OpenAccountRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.open(user.userId(), request.currency());
    }
}

package com.finguard.auth.controller;

import com.finguard.auth.dto.AuthResponse;
import com.finguard.auth.dto.LoginRequest;
import com.finguard.auth.dto.RegisterRequest;
import com.finguard.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and login endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return authService.register(request, correlationId);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT token")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return authService.login(request, correlationId);
    }
}

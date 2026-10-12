package com.finguard.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OpenAccountRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an ISO 4217 code") String currency
) {}

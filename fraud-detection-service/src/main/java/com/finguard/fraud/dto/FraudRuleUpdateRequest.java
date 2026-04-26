package com.finguard.fraud.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FraudRuleUpdateRequest(
        Boolean enabled,

        @Min(value = 1, message = "Weight must be at least 1")
        @Max(value = 100, message = "Weight cannot exceed 100")
        Integer weight
) {}

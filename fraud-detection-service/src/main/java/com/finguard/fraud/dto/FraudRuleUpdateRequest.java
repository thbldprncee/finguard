package com.finguard.fraud.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Partial update: each field is optional, but a request must change at least one of them. */
public record FraudRuleUpdateRequest(
        Boolean enabled,

        @Min(value = 1, message = "Weight must be at least 1")
        @Max(value = 100, message = "Weight cannot exceed 100")
        Integer weight
) {

    @JsonIgnore
    @AssertTrue(message = "At least one of enabled or weight must be provided")
    public boolean isAnyFieldPresent() {
        return enabled != null || weight != null;
    }
}

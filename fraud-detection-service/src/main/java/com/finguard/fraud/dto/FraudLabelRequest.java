package com.finguard.fraud.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An analyst's verdict: was the transaction really fraud? */
public record FraudLabelRequest(
        @NotNull Boolean fraud,
        @Size(max = 1000) String note
) {
}

package com.finguard.fraud.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String role) {

    /** ANALYST and ADMIN see full fraud analyses. */
    public boolean isStaff() {
        return "ANALYST".equals(role) || "ADMIN".equals(role);
    }
}

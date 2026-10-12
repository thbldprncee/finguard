package com.finguard.transaction.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String role) {

    /** ANALYST and ADMIN may see every user's transactions. */
    public boolean isStaff() {
        return "ANALYST".equals(role) || "ADMIN".equals(role);
    }
}

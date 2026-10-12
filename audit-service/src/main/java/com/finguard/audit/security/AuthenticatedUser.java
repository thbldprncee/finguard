package com.finguard.audit.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String role) {

    /** ANALYST and ADMIN may read the audit trail. */
    public boolean isStaff() {
        return "ANALYST".equals(role) || "ADMIN".equals(role);
    }
}

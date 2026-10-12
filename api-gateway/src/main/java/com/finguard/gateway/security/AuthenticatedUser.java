package com.finguard.gateway.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String role) {

    /** ANALYST and ADMIN receive every user's live events. */
    public boolean isStaff() {
        return "ANALYST".equals(role) || "ADMIN".equals(role);
    }
}

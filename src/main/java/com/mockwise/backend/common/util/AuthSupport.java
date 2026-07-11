package com.mockwise.backend.common.util;

import com.mockwise.backend.auth.SupabaseUser;
import org.springframework.security.core.Authentication;

public final class AuthSupport {

    private AuthSupport() {}

    public static SupabaseUser requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authentication found");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUser user) {
            return user;
        }
        throw new IllegalArgumentException(
                "Invalid authentication type: " + principal.getClass().getSimpleName());
    }
}

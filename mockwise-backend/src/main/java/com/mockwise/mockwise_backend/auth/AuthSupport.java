package com.mockwise.mockwise_backend.auth;

import com.mockwise.mockwise_backend.common.exception.UnauthenticatedException;
import org.springframework.security.core.Authentication;

/**
 * Helpers for resolving the authenticated Supabase principal from the security context.
 */
public final class AuthSupport {

    private AuthSupport() {
    }

    public static SupabaseAuthService.SupabaseUser requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw UnauthenticatedException.missing();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseAuthService.SupabaseUser user) {
            return user;
        }

        throw UnauthenticatedException.missing();
    }

    /**
     * Returns the user when present and valid; otherwise null (for optional-auth endpoints).
     */
    public static SupabaseAuthService.SupabaseUser optionalUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseAuthService.SupabaseUser user) {
            return user;
        }
        return null;
    }
}

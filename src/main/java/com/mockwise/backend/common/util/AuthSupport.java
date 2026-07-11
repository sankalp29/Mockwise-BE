package com.mockwise.backend.common.util;

import com.mockwise.backend.auth.SupabaseUser;
import com.mockwise.backend.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;

public final class AuthSupport {

    private AuthSupport() {}

    public static SupabaseUser requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUser user) {
            return user;
        }
        throw new UnauthorizedException("Authentication is required to access this resource.");
    }
}

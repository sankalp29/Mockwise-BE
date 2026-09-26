package com.mockwise.backend.config;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

class AuthSupportTest {

    @Test
    void requireUserReturnsPrincipal() {
        SupabaseUser user = new SupabaseUser("u1", "a@b.com", true);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, java.util.Collections.emptyList());
        assertEquals("u1", AuthSupport.requireUser(auth).getId());
    }

    @Test
    void requireUserRejectsNullAuth() {
        assertThrows(UnauthorizedException.class, () -> AuthSupport.requireUser(null));
    }

    @Test
    void requireUserRejectsWrongPrincipalType() {
        Authentication auth = new UsernamePasswordAuthenticationToken("string-principal", null);
        assertThrows(UnauthorizedException.class, () -> AuthSupport.requireUser(auth));
    }
}

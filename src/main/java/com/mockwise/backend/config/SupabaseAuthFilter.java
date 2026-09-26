package com.mockwise.backend.config;

import com.mockwise.backend.service.auth.SupabaseAuthService;
import com.mockwise.backend.service.auth.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class SupabaseAuthFilter extends OncePerRequestFilter {

    static final SupabaseUser GUEST_USER =
            new SupabaseUser("bypass-test-user", "test@mockwise.local", true);

    private final SupabaseAuthService supabaseAuthService;
    private final UserAccountService userAccountService;
    private final boolean authDisabled;

    public SupabaseAuthFilter(
            SupabaseAuthService supabaseAuthService,
            UserAccountService userAccountService,
            @Value("${mockwise.auth.disabled:false}") boolean authDisabled) {
        this.supabaseAuthService = supabaseAuthService;
        this.userAccountService = userAccountService;
        this.authDisabled = authDisabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        log.info("Processing request: {} {}", request.getMethod(), requestPath);

        if (authDisabled) {
            UsernamePasswordAuthenticationToken guestAuth =
                    new UsernamePasswordAuthenticationToken(GUEST_USER, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(guestAuth);
            userAccountService.ensure(GUEST_USER);
            log.info("Auth disabled; using guest user for {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        log.info("Authorization header present: {}", authHeader != null);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.info("Extracted token length: {}", token.length());
            try {
                SupabaseUser user = supabaseAuthService.verifyToken(token);
                if (user != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    userAccountService.ensure(user);
                    log.info("Successfully authenticated user: {}", user.getEmail());
                } else {
                    log.warn("Token verification returned null user");
                }
            } catch (Exception e) {
                log.error("Failed to authenticate Supabase token: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No valid Authorization header found");
        }

        filterChain.doFilter(request, response);
    }
}

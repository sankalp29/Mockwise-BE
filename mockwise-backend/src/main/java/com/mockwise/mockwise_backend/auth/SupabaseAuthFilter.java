package com.mockwise.mockwise_backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupabaseAuthFilter extends OncePerRequestFilter {

    private final SupabaseAuthService supabaseAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        log.info("Processing request: {} {}", request.getMethod(), requestPath);
        
        String authHeader = request.getHeader("Authorization");
        log.info("Authorization header present: {}", authHeader != null);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.info("Extracted token length: {}", token.length());
            
            try {
                SupabaseAuthService.SupabaseUser user = supabaseAuthService.verifyToken(token);
                
                if (user != null) {
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
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

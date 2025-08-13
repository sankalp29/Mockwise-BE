package com.mockwise.mockwise_backend.auth;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RememberMeServices rememberMeServices) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    String uri = req.getRequestURI();
                    String accept = req.getHeader("Accept");
                    String requestedWith = req.getHeader("X-Requested-With");
                    boolean isApi = uri != null && uri.startsWith("/api/");
                    boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);
                    boolean wantsJson = accept != null && accept.contains("application/json");
                    if (isApi || isAjax || wantsJson) {
                        res.sendError(401);
                    } else {
                        res.sendRedirect("http://localhost:5173/home");
                    }
                }))
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/", "/login", "/login/oauth2/**", "/oauth2/**", "/static/**", "/favicon.ico", "/api/auth/signup", "/api/auth/verify", "/api/auth/login", "/api/auth/password/**").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                                .successHandler(authenticationSuccessHandler())
                                .failureHandler((req, res, ex) -> res.sendRedirect("http://localhost:5173/home?oauth_error=1"))
                )
                .rememberMe(r -> r.rememberMeServices(rememberMeServices))
                .logout(logout -> logout.logoutSuccessUrl("http://localhost:5173/home"));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("http://localhost:5173/home");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public RememberMeServices rememberMeServices(AppUserDetailsService userDetailsService) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices("mockwise-remember-key", userDetailsService);
        services.setTokenValiditySeconds(30 * 24 * 60 * 60);
        services.setAlwaysRemember(true);
        return services;
    }
}
package com.mockwise.backend.config;

import com.mockwise.backend.config.JsonAccessDeniedHandler;
import com.mockwise.backend.config.JsonAuthEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SupabaseAuthFilter supabaseAuthFilter;
    private final JsonAuthEntryPoint jsonAuthEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;
    private final boolean authDisabled;

    public SecurityConfig(SupabaseAuthFilter supabaseAuthFilter,
                          JsonAuthEntryPoint jsonAuthEntryPoint,
                          JsonAccessDeniedHandler jsonAccessDeniedHandler,
                          @Value("${mockwise.auth.disabled:false}") boolean authDisabled) {
        this.supabaseAuthFilter = supabaseAuthFilter;
        this.jsonAuthEntryPoint = jsonAuthEntryPoint;
        this.jsonAccessDeniedHandler = jsonAccessDeniedHandler;
        this.authDisabled = authDisabled;
    }

    @Bean
    @SuppressWarnings("java:S4502") // Stateless API. Callers authenticate with a bearer token, not a cookie.
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonAuthEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                if (authDisabled) {
                    auth.anyRequest().permitAll();
                } else {
                    auth.requestMatchers(
                            "/",
                            "/health",
                            "/error",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/v3/api-docs.yaml",
                            "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated();
                }
            })
            .addFilterBefore(supabaseAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "https://mockwise.in",
            "https://mockwise-be-production.up.railway.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

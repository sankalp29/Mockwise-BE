package com.mockwise.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseAuthService {

    private final WebClient supabaseAdminClient;
    private final ObjectMapper objectMapper;

    @Value("${supabase.service.key}")
    private String serviceKey;

    public SupabaseUser verifyToken(String token) {
        try {
            log.info("Verifying Supabase token...");
            String[] chunks = token.split("\\.");
            if (chunks.length != 3) {
                log.error("Invalid JWT token format");
                return null;
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(chunks[1]));
            log.info("JWT payload: {}", payload);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(payload);
            String userId = jsonNode.get("sub").asText();
            String email = jsonNode.get("email").asText();
            log.info("Extracted from JWT - User ID: {}, Email: {}", userId, email);
            return new SupabaseUser(userId, email, true);
        } catch (Exception e) {
            log.error("Failed to verify Supabase token: ", e);
            return null;
        }
    }

    public Mono<SupabaseUser> getUserById(String userId) {
        return supabaseAdminClient
                .get()
                .uri("/auth/v1/admin/users/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseUserResponse)
                .doOnError(e -> log.error("Failed to fetch user from Supabase: ", e));
    }

    public Mono<SupabaseUser> createUser(String email, String password) {
        String requestBody = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "email_confirm": true
            }
            """, email, password);

        return supabaseAdminClient
                .post()
                .uri("/auth/v1/admin/users")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseUserResponse)
                .doOnError(e -> log.error("Failed to create user in Supabase: ", e));
    }

    private SupabaseUser parseUserResponse(String response) {
        try {
            JsonNode userNode = objectMapper.readTree(response);
            String id = userNode.get("id").asText();
            String email = userNode.get("email").asText();
            boolean emailVerified = userNode.get("email_confirmed_at") != null
                    && !userNode.get("email_confirmed_at").isNull();
            return new SupabaseUser(id, email, emailVerified);
        } catch (Exception e) {
            log.error("Failed to parse Supabase user response: ", e);
            throw new RuntimeException("Failed to parse user data", e);
        }
    }
}

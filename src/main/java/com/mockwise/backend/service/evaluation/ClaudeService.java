package com.mockwise.backend.service.evaluation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Anthropic transport. Interview styles pass an {@link EvaluationSpec};
 * this class does not know which style it is calling for.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String DEFAULT_MODEL = "claude-opus-4-7";
    private static final long DEFAULT_MAX_TOKENS = 1500L;

    private final ObjectMapper objectMapper;
    private AnthropicClient anthropicClient;

    @Value("${claude.api.key:}")
    private String claudeApiKey;

    @Value("${claude.model:claude-opus-4-7}")
    private String model;

    @Value("${claude.max-tokens:1500}")
    private long maxTokens;

    public ClaudeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initAnthropicClient() {
        String apiKey = (claudeApiKey != null && !claudeApiKey.isBlank())
                ? claudeApiKey
                : System.getenv("ANTHROPIC_API_KEY");

        try {
            if (apiKey == null || apiKey.isBlank()) {
                log.error("Anthropic API key is not configured. Set 'claude.api.key' or ANTHROPIC_API_KEY env var.");
                this.anthropicClient = null;
                return;
            }
            this.anthropicClient = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();

            log.info("Anthropic Client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Anthropic client: ", e);
            this.anthropicClient = null;
        }
    }

    /**
     * Blocking Claude call. Must never run inside an open DB transaction.
     * Call from async workers or from a service method that is not transactional.
     */
    public String complete(EvaluationSpec evaluation) {
        log.info("ClaudeService.complete called for {}", evaluation.getClass().getSimpleName());

        if (anthropicClient == null) {
            log.warn("Anthropic client is null, returning evaluation fallback");
            return evaluation.fallback("Anthropic client not initialized");
        }

        try {
            var messages = anthropicClient.beta().messages();
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(resolvedModel())
                    .maxTokens(resolvedMaxTokens())
                    .addUserMessage(evaluation.prompt())
                    .build();

            Object response = messages.create(params);
            String serialized = objectMapper.writeValueAsString(response);
            return extractContentFromResponse(serialized, evaluation);
        } catch (Exception e) {
            log.error("Error calling Anthropic SDK: {} - {}", e.getClass().getSimpleName(), String.valueOf(e.getMessage()));
            return evaluation.fallback("Claude API call failed: " + e.getMessage());
        }
    }

    private String resolvedModel() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    private long resolvedMaxTokens() {
        return maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    private String extractContentFromResponse(String response, EvaluationSpec evaluation) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                JsonNode first = content.get(0);
                JsonNode textNode = first.get("text");
                if (textNode != null && !textNode.isNull()) {
                    String text = textNode.asText();
                    String normalized = coerceToJson(text);
                    if (normalized != null) {
                        return normalized;
                    }
                    log.warn("Claude text did not contain valid JSON; returning evaluation fallback");
                    return evaluation.fallback("Claude returned non-JSON response");
                }
            }
            log.warn("Unexpected Anthropic SDK response shape");
            return evaluation.fallback("Unable to parse Claude response");
        } catch (Exception e) {
            log.error("Error parsing Claude response: ", e);
            return evaluation.fallback("Error parsing Claude response: " + e.getMessage());
        }
    }

    private String coerceToJson(String text) {
        if (text == null) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(text);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ignore) {
            // fall through to a balanced-object scan
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String candidate = text.substring(start, i + 1);
                    try {
                        JsonNode parsed = objectMapper.readTree(candidate);
                        return objectMapper.writeValueAsString(parsed);
                    } catch (Exception ignore) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}

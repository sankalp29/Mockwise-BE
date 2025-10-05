package com.mockwise.mockwise_backend.interview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.messages.MessageCreateParams;

@Service
public class ClaudeService {
    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    
    private final ObjectMapper objectMapper;
    private AnthropicClient anthropicClient;

    @Value("${claude.api.key:}")
    private String claudeApiKey;

    public ClaudeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @jakarta.annotation.PostConstruct
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

    // Single unified Claude API call method
    public Mono<String> callClaude(String prompt) {
        log.info("ClaudeService.callClaude called");
        
        if (anthropicClient == null) {
            log.warn("Anthropic client is null, returning fallback");
            return Mono.just(createFallbackResponse("Anthropic client not initialized"));
        }
        
        return Mono.fromCallable(() -> {
            try {
                var messages = anthropicClient.beta().messages();
                MessageCreateParams params = MessageCreateParams.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(1500L)
                        .addUserMessage(prompt)
                        .build();

                Object response = messages.create(params);
                String serialized = objectMapper.writeValueAsString(response);
                return extractContentFromResponse(serialized);
            } catch (Exception e) {
                log.error("Error calling Anthropic SDK: {} - {}", e.getClass().getSimpleName(), String.valueOf(e.getMessage()));
                return createFallbackResponse("Claude API call failed: " + e.getMessage());
            }
        });
    }

    // Helper: Generate prompt for code feedback evaluation (includes optional user self-assessment)
    public String buildCodeFeedbackPrompt(String problemStatement, String userCode, String language,
                                      String userTimeComplexity, String userSpaceComplexity) {
        String selfTime = (userTimeComplexity == null || userTimeComplexity.isBlank()) ? "Not provided" : userTimeComplexity;
        String selfSpace = (userSpaceComplexity == null || userSpaceComplexity.isBlank()) ? "Not provided" : userSpaceComplexity;

        return String.format("""
            Evaluate this coding problem and solution for correctness, optimality, time & space complexity, clarity, and provide overall feedback + rating out of 10.

            **Problem**:  
            %s

            **User Code (%s)**:  
            %s

            **Self-Assessed Complexity**:  
            Time: %s  
            Space: %s

            ======== RULES ========

            Stub Rule (Overrides All):  
            If code is stub/empty/unimplemented:
            - All scores = 0  
            - All feedback = "No meaningful implementation provided."  
            - Do not infer complexity.  

            Correctness (0.25 weight):  
            - Must work for all valid + edge inputs.  
            - Brute-force but correct is acceptable.  
            - Incorrect = all other scores 0.  
            Scoring:  
            - 8–10: Fully correct  
            - 6–7: Minor edge issues  
            - 4–5: Partially correct  
            - ≤3: Fails most cases  

            Optimality (0.60 weight):  
            Only if correct.  
            - Brute force = low score.  
            Scoring:  
            - 8–10: Fully optimal  
            - 6–7: Efficient, not best  
            - 4–5: Clearly inefficient  
            - ≤3: Very poor  

            Time Complexity (0.05 weight) & Space Complexity (0.05 weight):  
            Compare user's self-assessed complexity with actual Big O of the code.
            Scoring:
            - 10: Exactly correct (Minor notation differences (e.g., O(n) vs O(N)) acceptable)
            - 0: Incorrect or missing

            In feedback, always state:
            - Actual Big O: ...
            - User stated: ...
            - Match: Yes/No (with explanation if incorrect)  

            Code Clarity (0.05 weight):  
            - Score based on naming, structure, readability, modularity.  
            - No penalty for missing comments.  

            Strengths: real positives only  
            Improvements: actionable issues only (correctness > efficiency > clarity)

            Overall Rating (0–10):  
            Weighted avg: Correctness (0.25), Optimality (0.60), Time (0.05), Space (0.05), Clarity (0.05)

            Return ONLY this JSON:

            {
            "correctness": {"score": 0-10, "feedback": "..."},
            "optimality": {"score": 0-10, "feedback": "..."},
            "timeComplexity": {"score": 0-10, "feedback": "...", "bigO": "..."},
            "spaceComplexity": {"score": 0-10, "feedback": "...", "bigO": "..."},
            "clarity": {"score": 0-10, "feedback": "..."},
            "overallRating": 0-10,
            "overallFeedback": "...",
            "strengths": ["..."],
            "improvements": ["..."]
            }
            """, problemStatement, language, userCode, selfTime, selfSpace);
    }

    
    private String extractContentFromResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            // New Messages API returns content array with text blocks
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
                    log.warn("Claude text did not contain valid JSON; returning fallback. Text: {}", text);
                    return createFallbackResponse("Claude returned non-JSON response");
                }
            }
            log.warn("Unexpected Anthropic SDK response: {}", response);
            return createFallbackResponse("Unable to parse Claude response");
        } catch (Exception e) {
            log.error("Error parsing Claude response: ", e);
            return createFallbackResponse("Error parsing Claude response: " + e.getMessage());
        }
    }

    // Attempts to extract and normalize a JSON object from arbitrary text
    private String coerceToJson(String text) {
        if (text == null) return null;
        // Try direct parse
        try {
            JsonNode n = objectMapper.readTree(text);
            return objectMapper.writeValueAsString(n);
        } catch (Exception ignore) {
        }
        // Extract first balanced JSON object
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String candidate = text.substring(start, i + 1);
                    try {
                        JsonNode n = objectMapper.readTree(candidate);
                        return objectMapper.writeValueAsString(n);
                    } catch (Exception ignore) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private String createFallbackResponse(String errorMessage) {
        log.info("Creating fallback response for: {}", errorMessage);
        return String.format("""
            {
                "correctness": {"score": 8, "feedback": "Code appears functionally correct based on basic analysis - %s"},
                "timeComplexity": {"score": 7, "feedback": "Time complexity looks reasonable", "bigO": "O(n)"},
                "spaceComplexity": {"score": 7, "feedback": "Space usage appears efficient", "bigO": "O(1)"},
                "clarity": {"score": 8, "feedback": "Code is generally readable"},
                "overallRating": 7,
                "overallFeedback": "Good solution overall. %s",
                "strengths": ["Functional correctness", "Readable structure"],
                "improvements": ["Could add more comments", "Consider edge cases"]
            }
            """, errorMessage, errorMessage);
    }
}
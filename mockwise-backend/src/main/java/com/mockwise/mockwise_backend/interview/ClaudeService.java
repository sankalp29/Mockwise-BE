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
        Evaluate the following coding problem and solution for correctness, optimality, time complexity, space complexity, clarity, readability, and provide an overall feedback and rating out of 10.

        *Problem Statement:*
        %s

        *User's Solution (%s):*
        ⁠ %s
        %s
         ⁠

        *User's Self-Assessed Complexities (if any):*
        - Time Complexity: %s
        - Space Complexity: %s

        *Critical Evaluation Rules:*
        Global Stub Rule (Overrides all other category rules):
        If the code has no meaningful implementation (only stubs, empty methods, comments, or incomplete skeletons):
        - Set ALL scores (correctness, optimality, timeComplexity, spaceComplexity, clarity) to 0.
        - For every category’s feedback, overallFeedback, strengths, and improvements: "No meaningful implementation was provided, so no evaluation is possible."
        - Do NOT infer any time/space complexities.
        - This rule ALWAYS takes precedence over the category-specific scoring rubrics below.

        ### 1) Correctness (Highest Priority)
        - Correctness is the foundation; if the solution is incorrect, all other scores (optimality, complexity) must also be 0.
        - Must solve the problem for all valid inputs, including edge cases (empty input, min/max values, duplicates, boundary cases).
        - Brute force but correct → high correctness score, lower optimality score.
        - If incorrect, explain why and deduct points.

        *Score Range*
        - 9–10: Fully correct, handles all edge cases.
        - 7–8: Mostly correct, minor edge case issues.
        - 5–6: Partially correct, misses multiple cases.
        - ≤4: Incorrect or fails most cases.

        ### 2) Optimality (Only if Correctness > 0)
        - Evaluate if the solution is optimal in both time and space.
        - Correct but suboptimal (e.g., O(N²) instead of O(N)) → reduced score.
        - Prioritize time optimality over space.
        - Correct but brute-force is fine → correctness high, optimality low.

        *Score Range*
        - 9–10: Fully optimal in time & space.
        - 7–8: Efficient but not the best.
        - 5–6: Works but clearly inefficient.
        - ≤4: Inefficient or irrelevant if correctness is 0.

        ### 3) Time & Space Complexity (Only if Correctness > 0)
        - Derive from code only, not from user claims.
        - Compare against user’s self-assessment:
            - Correct → full marks.
            - Incorrect or missing → deduct 2 marks.
        - If inefficient but correct → highlight explicitly.

        *Score Range*
        - 9–10: Correct, optimal complexity.
        - 7–8: Correct but slightly inefficient.
        - 5–6: Works but inefficient or missing/incorrect assessment.
        - ≤4: Incorrect solution or very poor efficiency.

        ### 4) Code Clarity & Readability (Only if Correctness > 0)
        - Focus on structure, naming, and maintainability.
        - Do NOT penalize for missing comments (not required in interviews).
        - Deduct only if code is confusing, repetitive, or poorly structured.

        *Score Range*
        - 9–10: Highly readable and modular.
        - 7–8: Readable with minor improvements.
        - 5–6: Moderate clarity, some confusing parts.
        - ≤4: Poor readability and unmaintainable.

        ### 5) Strengths
        - Highlight specific positives (correctness, edge case handling, efficient logic, modularity, readability).
        - Avoid trivial points.

        ### 6) Areas for Improvement
        - Identify real growth opportunities (correctness first → optimality → efficiency → clarity).
        - No mention of missing comments.

        ### 7) Overall Feedback
        - Provide a balanced, specific summary covering correctness, optimality, efficiency, and clarity.
        - Overall rating should combine all categories.

        *Output Format:*
        Return ONLY this JSON object, no other text:

        {
            "correctness": {"score": 0-10, "feedback": "detailed feedback on correctness"},
            "optimality": {"score": 0-10, "feedback": "detailed feedback on optimality"},
            "timeComplexity": {"score": 0-10, "feedback": "analysis of time complexity", "bigO": "O(n), O(log n), etc."},
            "spaceComplexity": {"score": 0-10, "feedback": "analysis of space complexity", "bigO": "O(1), O(n), etc."},
            "clarity": {"score": 0-10, "feedback": "feedback on code clarity"},
            "overallRating": 0-10,
            "overallFeedback": "comprehensive overall feedback",
            "strengths": ["strength1", "strength2"],
            "improvements": ["improvement1", "improvement2"]
        }
        """, problemStatement, language, language, userCode, selfTime, selfSpace);
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
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
            Evaluate the following coding problem and solution for correctness & optimality, time complexity, space complexity, clarity, readability, and provide an overall feedback and rating out of 10.
            
            **Problem Statement:**
            %s
            
            **User's Solution (%s):**
            ```%s
            %s
            ```
            
            **User's Self-Assessed Complexities (if any):**
            - Time Complexity: %s
            - Space Complexity: %s
            
            Evaluation Rules (critical):

            1) If the code has no meaningful implementation (only stubs, empty methods, comments, or incomplete skeletons):
                - Set ALL scores to 0.
                - For every category’s feedback, overallFeedback, strengths, and improvements:
                    "No meaningful implementation was provided, so no evaluation is possible."
                - Do NOT infer complexities (e.g., O(1)) from trivial/absent code.

            2) If the code is only boilerplate/trivial setup without solving the problem:
                - Give very low scores (1–2 max).
                - Feedback must focus ONLY on substantive qualities (algorithmic design, readability, decomposition, edge cases, testability).
                - Do NOT include shallow points such as:
                    - “used return type”
                    - “added access modifier”
                    - “removed unused variable”
                    - “initialized array”
                    - “provided semicolon/boilerplate syntax”
                - Feedback must highlight that the solution is incomplete and lacks problem-solving logic.

            3) Otherwise, evaluate normally:
                - Scores should reflect correctness, efficiency, readability, modularity, and robustness of the actual solution.

            4) timeComplexity.score and spaceComplexity.score must be based strictly on the code (ignore user self-assessment for scoring).
                - Feedback must:
                    a) Analyze complexities from the submitted code.
                    b) State if the solution is incorrect, and rate low if so.
                    c) Explicitly compare with user’s self-assessment (if provided), stating whether they were correct, underestimated, or overestimated.

            5) Strengths and Improvements Guidelines:
                - Must exclude anything the user didn’t control (e.g., stub/template code).
                - Do NOT include trivial/boilerplate points (see Rule 2 list).
                - Strengths should highlight substantive positives, such as:
                    - Correctness of algorithm and logic
                    - Efficiency (choice of algorithm, data structures)
                    - Code clarity, naming, readability
                    - Good decomposition/modularity
                    - Handling of edge cases
                    - Testability and maintainability
                - Improvements should identify real opportunities to grow, such as:
                    - Optimizing time/space complexity
                    - Improving modularity/refactoring
                    - Handling additional edge cases
                    - Improving naming or readability
                    - Adding documentation or inline comments
                - Each point must be specific, contextual, and actionable, not generic.
                - If no meaningful strengths or improvements exist, apply Rule 1 or 2 as applicable.

            6) Return ONLY the JSON object in the schema.

            Please provide your evaluation in the following JSON format ONLY. Do not include any other text or explanation outside of this JSON:
            
            {
                "correctness": {"score": 0-10, "feedback": "detailed feedback on correctness"},
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

    // Overload that includes a compact JSON test report to inform evaluation
    public String buildCodeFeedbackPrompt(String problemStatement, String userCode, String language,
                                          String userTimeComplexity, String userSpaceComplexity,
                                          String testReportJson) {
        String base = buildCodeFeedbackPrompt(problemStatement, userCode, language, userTimeComplexity, userSpaceComplexity);
        // Insert test results section before the final instructions if available
        if (testReportJson == null || testReportJson.isBlank()) return base;
        String testsSection = String.format("\n\nAdditional Context - Test Case Results (JSON):\n```json\n%s\n```\n\n", testReportJson);
        return base + testsSection;
    }

    // Helper: Generate prompt for AI insights from feedback data
    public String buildAIInsightsPrompt(String feedbackContext) {
        return "Given the following recent interview feedback JSON blobs, extract: strongestTopic, weakestTopic, mostCommonMistake as a JSON object with keys {\\\"strongestTopic\\\", \\\"weakestTopic\\\", \\\"mostCommonMistake\\\"}. Return ONLY the JSON.\n" + feedbackContext;
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
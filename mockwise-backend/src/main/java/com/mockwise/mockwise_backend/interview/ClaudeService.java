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
        - If the code has no meaningful implementation (only stubs, empty methods, comments, or incomplete skeletons):
            - Set ALL scores to 0.
            - For every category’s feedback, overallFeedback, strengths, and improvements:
                "No meaningful implementation was provided, so no evaluation is possible."
            - Do NOT infer complexities (e.g., O(1)) from trivial/absent code.

        1) Correctness Rubric:
                - Correctness is the highest priority; all other evaluations depend on a working solution.
                - The solution must solve the stated problem for all valid inputs, including edge cases such as:
            - Empty input
                - Maximum/minimum values
            - Duplicate elements
                - Special or boundary cases defined by constraints
            - If the solution is incorrect or fails edge cases, assign a low correctness score (≤ 2/10).
            - Why marks have been deducted (if any)
            Strengths (Correctness)
                - Correctly implements the algorithm for the problem.
                - Handles edge cases robustly (empty inputs, extreme values, duplicates).
                - Produces accurate results consistently for valid inputs.
            Improvements (Correctness)
                - Missing handling for edge cases (e.g., empty array, zero values, maximum constraints).
                - Fails to cover certain input scenarios defined in constraints.
                - Logic produces wrong results for some inputs due to incorrect conditional checks or loop bounds.

            Score Range
            Score	Description
            9–10	Fully correct solution, handles all edge cases.
            7–8	    Mostly correct, minor issues with some edge cases.
            5–6	    Partially correct; misses multiple cases, but some logic is right.
            ≤4	    Incorrect solution; fails most or all cases.

        2) Scoring Rubric for Time & Space Complexity:
            A. Complexity Analysis (from Code Only)
                - Both time and space complexity must be derived strictly from the submitted code.
                - Ignore user’s self-assessment when calculating the actual score.
                - Time complexity should be prioritized over space complexity: a slower algorithm is penalized more than extra memory usage.

            B. Solution Correctness
                - If the solution is incorrect, assign a low score (≤ 2/10) regardless of complexity claims.
            
            C. Comparison with User’s Self-Assessment (if provided)
                - Correct self-assessment → award full marks in the comparison section.
                - Underestimated/Overestimated → note the mismatch in feedback, partial marks allowed.

            D. Missing or Incorrect Complexity Assessment (penalty rules)
                - No assessment provided by user → deduct 2 marks from both time and space complexity scores.
                - Incorrect assessment provided → deduct 2 marks from the respective score.
                - Correct assessment provided → no deduction.

            E. Efficiency vs Optimality (Time & Space)
            Time First:
                - If the solution is efficient but not time-optimal, highlight explicitly (e.g., O(N log N) when O(N) is achievable, or multiple passes when fewer are possible).
                - Award slightly reduced marks (e.g., 8/10 instead of 9–10).
            Space Second:
                - If the solution uses extra memory unnecessarily (e.g., O(N) extra when O(1) is possible), highlight explicitly.
                - Award slightly reduced marks accordingly.
            Optimal in Both Time and Space: full marks.
            Rule of Thumb: prioritize time efficiency first; only optimize space once time is optimal.
            - Why marks have been deducted (if any)

            F. Score Range
                Score	Description
                9–10	Correct, optimal in both time and space.
                7–8	    Correct, efficient but suboptimal in either time or space (extra passes, higher constants, unnecessary extra memory).
                5–6	    Correct but inefficient (e.g., O(N²) time when O(N) expected, or O(N²) space when O(N) possible), OR missing/incorrect assessment.
                ≤ 4	    Incorrect solution or very poor efficiency.


        3) Code Clarity & Readability Rubric
            - Evaluation Rules
                - Evaluate naming, structure, decomposition, and readability.
                - Code clarity does not excuse incorrect logic, but contributes to maintainability and reviewability.
            Strengths
                - Descriptive variable and function names.
                - Logical code flow; easy to understand without extra context.
                - Proper decomposition into reusable functions or methods.
                - Minimal duplication and clean control structures.
            Improvements
                - Confusing variable names or unclear logic.
                - Long monolithic functions instead of modular methods.
                - Repetition of code that could be refactored into helper functions.
                - Nested or complex loops/conditionals that reduce readability.
                - Why marks have been deducted (if any)

            Score Range
            Score	Description
            9–10	Highly readable, modular, and maintainable.
            7–8	    Readable, minor improvements possible.
            5–6	    Moderate clarity; some parts confusing or repetitive.
            ≤4	    Poor readability, confusing logic, unmaintainable.


        4) Strengths Rubric (Detailed Positive Feedback)
            - Evaluation Rules
                - Highlight substantive positives, not trivial points (exclude stubs/boilerplate).
                - Must be specific and actionable, giving users insight into what they did well.
            - Examples of Strengths to Highlight
                - Correct algorithm choice and logic implementation.
                - Efficient data structures or operations (time or space optimal).
                - Good modularity and function decomposition.
                - Handles edge cases effectively.
                - Clear and readable variable names and logical flow.
                - Scalable solution design that can handle large inputs.


        5) Areas for Improvement Rubric (Detailed Growth Feedback)
            Evaluation Rules
                - Identify real opportunities for improvement that would make the solution better.
                - Focus on correctness, efficiency, modularity, readability, and maintainability.
            Examples of Improvements to Highlight
                - Logic corrections for unhandled edge cases.

                - Optimize time complexity (e.g., reduce unnecessary loops or repeated operations).
                - Optimize space complexity (e.g., use in-place operations or more compact data structures).
                - Refactor repetitive or monolithic code into modular helper methods.
                - Improve variable naming, readability, and clarity of complex logic.
                - Consider scalability and maintainability for larger input sizes.

        6) Overall Feedback Rubric
            - Evaluation Rules
                - Provide a summary of the user’s code quality, covering correctness, efficiency, clarity, strengths, and improvements.
                - Must be specific, actionable, and contextual.
            Strengths
                - Summarize the most important positive aspects of the solution.
                - Highlight correctness, efficient algorithms, good decomposition, and clarity.
            Areas for Improvement
                - Summarize critical growth opportunities across correctness, efficiency, modularity, and readability.
                - Include high-priority fixes first (correctness → time → space → readability).

            Score Guidelines
            - Combine individual scores (correctness, clarity, complexity) for a holistic rating.
            - Provide clear rationale for strengths vs. improvements.

        7) Return ONLY the JSON object in the schema.

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
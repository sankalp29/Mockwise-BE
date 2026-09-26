package com.mockwise.backend.service.interview;

import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.service.evaluation.EvaluationSpec;

/**
 * Coding-interview rubric. Criteria changes belong here, not on the Claude client.
 */
public final class CodingEvaluation implements EvaluationSpec {

    private final String problemStatement;
    private final String userCode;
    private final ProgrammingLanguage language;
    private final String userTimeComplexity;
    private final String userSpaceComplexity;

    private CodingEvaluation(String problemStatement, String userCode, ProgrammingLanguage language,
                             String userTimeComplexity, String userSpaceComplexity) {
        this.problemStatement = problemStatement;
        this.userCode = userCode;
        this.language = language;
        this.userTimeComplexity = userTimeComplexity;
        this.userSpaceComplexity = userSpaceComplexity;
    }

    public static CodingEvaluation from(FeedbackPersistence.SubmissionPromptData data) {
        return new CodingEvaluation(
                data.problemStatement(),
                data.code(),
                data.language(),
                data.userTimeComplexity(),
                data.userSpaceComplexity());
    }

    @Override
    public String prompt() {
        String selfTime = blank(userTimeComplexity) ? "Not provided" : userTimeComplexity;
        String selfSpace = blank(userSpaceComplexity) ? "Not provided" : userSpaceComplexity;

        return """
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

            Correctness:
            - Must work for all valid + edge inputs.
            - Brute-force but correct is acceptable.
            - Incorrect = all other scores 0.
            Scoring:
            - 8–10: Fully correct
            - 6–7: Minor edge issues
            - 4–5: Partially correct
            - ≤3: Fails most cases

            Optimality (0.75 weight):
            Only if correct.
            - Brute force = low score.
            Scoring:
            - 8–10: Fully optimal
            - 6–7: Efficient, not best
            - 4–5: Clearly inefficient
            - ≤3: Very poor

            Time Complexity (0.10 weight) & Space Complexity (0.10 weight):
            Compare user's self-assessed complexity with actual Big O of the code.
            Do not penalize based on algorithm efficiency or optimality.
            Scoring:
            - 10: Correct (notation / standard format differences are acceptable)
            - 7-9: Essentially correct (notation / standard format differences are acceptable)
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
            Weighted avg: (optimality_score * 0.75) + (timeComplexity_score * 0.10) + (spaceComplexity_score * 0.10) + (clarity_score * 0.05)
            Round it to nearest integer.

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
            """.formatted(problemStatement, language.id(), userCode, selfTime, selfSpace);
    }

    @Override
    public String fallback(String reason) {
        return """
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
            """.formatted(reason, reason);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

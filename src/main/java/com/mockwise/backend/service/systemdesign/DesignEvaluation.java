package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.service.evaluation.EvaluationSpec;

/**
 * System-design rubric. Criteria changes belong here, not on the Claude client.
 */
public final class DesignEvaluation implements EvaluationSpec {

    private final String title;
    private final String brief;
    private final String requirements;
    private final String constraints;
    private final String scene;
    private final String caption;

    private DesignEvaluation(String title, String brief, String requirements, String constraints,
                             String scene, String caption) {
        this.title = title == null ? "" : title;
        this.brief = brief == null ? "" : brief;
        this.requirements = requirements == null ? "" : requirements;
        this.constraints = constraints == null ? "" : constraints;
        this.scene = scene == null ? "" : scene;
        this.caption = caption == null ? "" : caption;
    }

    public static DesignEvaluation of(DesignPrompt prompt, String scene, String caption) {
        if (prompt == null) {
            return new DesignEvaluation("", "", "", "", scene, caption);
        }
        return new DesignEvaluation(
                prompt.getTitle(),
                prompt.getBrief(),
                prompt.getRequirements(),
                prompt.getConstraints(),
                scene,
                caption);
    }

    @Override
    public String prompt() {
        return """
            Review this system-design interview. Score the drawing and the candidate's notes. Do not grade source code.

            **Prompt**: %s
            **Brief**:
            %s

            **Requirements**:
            %s

            **Constraints**:
            %s

            **Drawing (scene JSON)**:
            %s

            **Candidate notes**:
            %s

            ======== RULES ========

            Score each category from 0 to 10.
            An empty or placeholder board scores 3 or lower on architecture.
            Talk about this prompt. Do not invent components that are not on the board or in the notes.

            problemFraming: user, write path, and read path are named.
            architecture: boxes match the request path from client to storage.
            scalability: a bottleneck is named, with what would be replicated or cached.
            tradeoffs: one decision states what was given up.
            communication: boxes are labeled with the role they play.

            overallRating is the average of those five scores, rounded to the nearest integer.

            Return ONLY this JSON:

            {
            "problemFraming": {"score": 0-10, "feedback": "..."},
            "architecture": {"score": 0-10, "feedback": "..."},
            "scalability": {"score": 0-10, "feedback": "..."},
            "tradeoffs": {"score": 0-10, "feedback": "..."},
            "communication": {"score": 0-10, "feedback": "..."},
            "strengths": ["..."],
            "improvements": ["..."],
            "overallFeedback": "...",
            "overallRating": 0-10
            }
            """.formatted(title, brief, requirements, constraints, scene, caption);
    }

    @Override
    public String fallback(String reason) {
        int marks = DesignScene.shapeCount(scene);
        boolean drawn = marks > 0 && scene.length() > 40;
        int score = drawn ? Math.min(9, 6 + Math.min(3, marks / 2)) : 3;
        String architecture = drawn
                ? "The board has " + marks + " shapes. Walk through one request from the client to storage."
                : "The board was empty, so the architecture could not be evaluated.";
        String summary = drawn
                ? "The drawing gives a starting architecture. Tighten the data flow and the scale numbers next. " + reason
                : "Submit a diagram of the core path so the review can talk about concrete components. " + reason;
        return """
                {"problemFraming":{"score":%d,"feedback":"State the user, the write path, and the read path before adding extra boxes."},"architecture":{"score":%d,"feedback":"%s"},"scalability":{"score":%d,"feedback":"Name the bottleneck and what you would replicate or cache."},"tradeoffs":{"score":%d,"feedback":"Pick one decision, such as consistency versus latency, and say what you gave up."},"communication":{"score":%d,"feedback":"Label every box with the role it plays."},"strengths":["%s"],"improvements":["%s"],"overallFeedback":"%s","overallRating":%d}
                """.formatted(
                score,
                score,
                architecture,
                Math.max(3, score - 1),
                Math.max(3, score - 1),
                drawn ? score : 4,
                drawn ? "A diagram was submitted" : "The session was submitted",
                drawn ? "Add failure handling and data estimates" : "Draw the core request path",
                jsonSafe(summary),
                score
        );
    }

    private static String jsonSafe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "'");
    }
}

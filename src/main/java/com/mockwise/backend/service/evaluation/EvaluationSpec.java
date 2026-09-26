package com.mockwise.backend.service.evaluation;

/**
 * One interview style's instructions for the model.
 * The style owns the prompt and the JSON used when the model call cannot be used.
 * Model name and token limit stay on {@link ClaudeService}.
 */
public interface EvaluationSpec {

    String prompt();

    String fallback(String reason);
}

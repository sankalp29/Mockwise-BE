package com.mockwise.backend.service.interview;

/**
 * Result of the synchronous feedback gate. The controller maps this onto the HTTP body.
 */
public enum FeedbackRequestOutcome {
    STARTED,
    READY
}

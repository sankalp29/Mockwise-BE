package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.service.evaluation.ClaudeService;
import com.mockwise.backend.service.evaluation.EvaluationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignReviewerTest {

    @Mock private ClaudeService claudeService;
    @InjectMocks private DesignReviewer reviewer;

    @Test
    void reviewSendsADesignEvaluationAndReadsItsRating() {
        DesignPrompt prompt = new DesignPrompt();
        prompt.setTitle("URL Shortener");
        when(claudeService.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"overallRating\":8,\"overallFeedback\":\"Solid path\"}");

        DesignReviewer.Review review = reviewer.review(prompt, "[]", "notes");

        ArgumentCaptor<EvaluationSpec> spec = ArgumentCaptor.forClass(EvaluationSpec.class);
        verify(claudeService).complete(spec.capture());
        assertInstanceOf(DesignEvaluation.class, spec.getValue());
        assertTrue(spec.getValue().prompt().contains("URL Shortener"));
        assertEquals(8, review.rating());
        assertTrue(review.body().contains("Solid path"));
    }
}

package com.mockwise.mockwise_backend.interview;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestRunnerController {

    private final TestRunnerService testRunnerService;
    private final SubmissionTestResultRepository resultRepository;
    private final UserSubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;

    public record RunTestsRequest(UUID interviewId, UUID questionId, String language, String sourceCode) {}

    @PostMapping("/run")
    public ResponseEntity<?> runTests(@RequestBody RunTestsRequest req, Authentication auth) {
        if (req.questionId() == null || req.language() == null || req.sourceCode() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }
        // Run and persist results for this interview's submissions if provided
        List<TestRunnerService.TestCaseResult> results = testRunnerService.runAll(req.questionId(), req.language(), req.sourceCode());

        if (req.interviewId() != null) {
            List<UserSubmission> subs = submissionRepository.findByInterviewId(req.interviewId());
            UserSubmission sub = subs.isEmpty() ? null : subs.get(0);
            if (sub != null) {
                for (TestRunnerService.TestCaseResult r : results) {
                    SubmissionTestResult ent = new SubmissionTestResult();
                    ent.setSubmission(sub);
                    ent.setTestCase(testRunnerService.getTestCase(r.testCaseId()));
                    ent.setPassed(r.passed());
                    ent.setActualJson(r.actualJson());
                    ent.setExpectedJson(null); // optional: copy from test case
                    ent.setTimeMs(r.timeMs());
                    ent.setMemoryKb(r.memoryKb());
                    ent.setStderr(r.stderr());
                    resultRepository.save(ent);
                }
            }
        }

        return ResponseEntity.ok(Map.of("results", results));
    }
}



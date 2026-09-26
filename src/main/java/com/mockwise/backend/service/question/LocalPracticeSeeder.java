package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.auth.AppUser;
import com.mockwise.backend.repository.auth.AppUserRepository;
import com.mockwise.backend.repository.auth.UserRole;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.service.auth.UserAccountService;
import com.mockwise.backend.repository.interview.InterviewQuestion;
import com.mockwise.backend.repository.interview.InterviewQuestionRepository;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.question.OptimalSolution;
import com.mockwise.backend.repository.question.OptimalSolutionRepository;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionCodeStub;
import com.mockwise.backend.repository.question.QuestionCodeStubRepository;
import com.mockwise.backend.repository.question.QuestionRepository;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
import com.mockwise.backend.service.codesyntax.LanguageToolchainRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fills the local database with practice questions, a stub and an optimal solution
 * for every supported language, and one completed interview the feedback page can open.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalPracticeSeeder implements ApplicationRunner {

    static final String LOCAL_USER_ID = "bypass-test-user";
    static final String LOCAL_USER_EMAIL = "test@mockwise.local";

    private final QuestionRepository questionRepository;
    private final QuestionCodeStubRepository questionCodeStubRepository;
    private final OptimalSolutionRepository optimalSolutionRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final LanguageToolchainRegistry languageToolchainRegistry;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> languages = languageToolchainRegistry.all().stream()
                .map(tool -> tool.languageId())
                .toList();
        for (QuestionCodeCatalog.SeedQuestion seed : QuestionCodeCatalog.questions()) {
            Question question = questionRepository.findFirstByTitleIgnoreCase(seed.title()).orElseGet(() -> {
                Question created = new Question();
                created.setTitle(seed.title());
                created.setDifficulty(Question.Difficulty.EASY);
                return created;
            });
            question.setDescription(seed.description());
            question.setExample(seed.example());
            question.setConstraints(seed.constraints());
            if (question.getDifficulty() == null) {
                question.setDifficulty(Question.Difficulty.EASY);
            }
            Question saved = questionRepository.save(question);
            writeSamples(saved, seed.samples(), languages);
        }
        for (Question question : questionRepository.findAll()) {
            if (questionCodeStubRepository.findFirstByQuestion_IdAndLanguageIgnoreCase(question.getId(), "java").isEmpty()) {
                writeSamples(question, genericSamples(), languages);
            }
        }
        seedFeedbackInterview();
        seedAdmin();
        log.info("Local practice data is ready for {} languages", languages.size());
    }

    private void seedAdmin() {
        AppUser admin = appUserRepository.findByEmailIgnoreCase(UserAccountService.ADMIN_EMAIL).orElseGet(AppUser::new);
        admin.setEmail(UserAccountService.ADMIN_EMAIL);
        admin.setRole(UserRole.ADMIN);
        appUserRepository.save(admin);
    }

    private void writeSamples(Question question, Map<String, QuestionCodeCatalog.Sample> samples, List<String> languages) {
        Instant now = Instant.now();
        for (String language : languages) {
            QuestionCodeCatalog.Sample sample = samples.get(language);
            if (sample == null) {
                continue;
            }
            if (questionCodeStubRepository.findFirstByQuestion_IdAndLanguageIgnoreCase(question.getId(), language).isEmpty()) {
                QuestionCodeStub stub = new QuestionCodeStub();
                stub.setQuestion(question);
                stub.setLanguage(language);
                stub.setStub(sample.stub());
                stub.setCreatedAt(now);
                questionCodeStubRepository.save(stub);
            }
            if (optimalSolutionRepository.findByQuestionIdAndLanguage(question.getId(), language).isEmpty()) {
                OptimalSolution solution = new OptimalSolution();
                solution.setQuestion(question);
                solution.setLanguage(language);
                solution.setCode(sample.optimal());
                optimalSolutionRepository.save(solution);
            }
        }
    }

    private void seedFeedbackInterview() {
        boolean alreadySeeded = interviewRepository
                .findByUserIdAndStatus(LOCAL_USER_ID, Interview.Status.COMPLETED)
                .stream()
                .anyMatch(interview -> interview.getUserEmail() != null
                        && interview.getUserEmail().equals(LOCAL_USER_EMAIL)
                        && interview.getOverallRating() != null
                        && interview.getOverallRating() == 8.0);
        if (alreadySeeded) {
            return;
        }
        Question twoSum = questionRepository.findFirstByTitleIgnoreCase("Two Sum").orElse(null);
        Question parens = questionRepository.findFirstByTitleIgnoreCase("Valid Parentheses").orElse(null);
        if (twoSum == null || parens == null) {
            return;
        }
        Interview interview = new Interview();
        interview.setUserId(LOCAL_USER_ID);
        interview.setUserEmail(LOCAL_USER_EMAIL);
        interview.setDifficulty(Question.Difficulty.EASY);
        interview.setNumQuestions(2);
        interview.setTimeMinutes(30);
        interview.setStartedAt(Instant.now().minusSeconds(1800));
        interview.setEndedAt(Instant.now().minusSeconds(600));
        interview.setStatus(Interview.Status.COMPLETED);
        interview.setOverallRating(8.0);
        interview.setAggregated(true);
        Interview saved = interviewRepository.save(interview);
        interviewQuestionRepository.save(new InterviewQuestion(saved, twoSum, 1));
        interviewQuestionRepository.save(new InterviewQuestion(saved, parens, 2));
        userSubmissionRepository.save(submission(saved, twoSum, "class Solution { public int[] twoSum(int[] nums, int target) { return new int[] {0, 1}; } }"));
        userSubmissionRepository.save(submission(saved, parens, "class Solution { public boolean isValid(String s) { return true; } }"));
    }

    private UserSubmission submission(Interview interview, Question question, String code) {
        UserSubmission submission = new UserSubmission();
        submission.setInterview(interview);
        submission.setQuestion(question);
        submission.setCode(code);
        submission.setLanguage("java");
        submission.setSubmittedAt(interview.getEndedAt());
        submission.setFeedbackGeneratedAt(interview.getEndedAt());
        submission.setUserTimeComplexity("O(n)");
        submission.setUserSpaceComplexity("O(n)");
        submission.setClaudeFeedback(FEEDBACK_JSON);
        return submission;
    }

    private static Map<String, QuestionCodeCatalog.Sample> genericSamples() {
        QuestionCodeCatalog.Sample java = new QuestionCodeCatalog.Sample(
                "class Solution {\n    public int solve(int[] nums) {\n        return 0;\n    }\n}\n",
                "class Solution {\n    public int solve(int[] nums) {\n        int best = 0;\n        for (int value : nums) best = Math.max(best, value);\n        return best;\n    }\n}\n");
        return Map.of(
                "java", java,
                "python", new QuestionCodeCatalog.Sample("class Solution:\n    def solve(self, nums):\n        return 0\n", "class Solution:\n    def solve(self, nums):\n        return max(nums) if nums else 0\n"),
                "cpp", new QuestionCodeCatalog.Sample("#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {\npublic:\n    int solve(vector<int>& nums) { return 0; }\n};\n", "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {\npublic:\n    int solve(vector<int>& nums) { return nums.empty() ? 0 : *max_element(nums.begin(), nums.end()); }\n};\n"),
                "javascript", new QuestionCodeCatalog.Sample("function solve(nums) {\n  return 0;\n}\n", "function solve(nums) {\n  return nums.length ? Math.max(...nums) : 0;\n}\n"),
                "typescript", new QuestionCodeCatalog.Sample("function solve(nums: number[]): number {\n  return 0;\n}\n", "function solve(nums: number[]): number {\n  return nums.length ? Math.max(...nums) : 0;\n}\n"),
                "go", new QuestionCodeCatalog.Sample("package main\n\nfunc solve(nums []int) int {\n    return 0\n}\n", "package main\n\nfunc solve(nums []int) int {\n    best := 0\n    for _, value := range nums {\n        if value > best { best = value }\n    }\n    return best\n}\n"),
                "rust", new QuestionCodeCatalog.Sample("fn solve(_nums: &[i32]) -> i32 {\n    0\n}\n", "fn solve(nums: &[i32]) -> i32 {\n    nums.iter().copied().max().unwrap_or(0)\n}\n"),
                "ruby", new QuestionCodeCatalog.Sample("def solve(nums)\n  0\nend\n", "def solve(nums)\n  nums.max || 0\nend\n"),
                "scala", new QuestionCodeCatalog.Sample("object Solution {\n  def solve(nums: Array[Int]): Int = 0\n}\n", "object Solution {\n  def solve(nums: Array[Int]): Int = if (nums.isEmpty) 0 else nums.max\n}\n"),
                "csharp", new QuestionCodeCatalog.Sample("public class Solution {\n    public int Solve(int[] nums) { return 0; }\n    public static void Main() {}\n}\n", "public class Solution {\n    public int Solve(int[] nums) { int best = 0; foreach (int value in nums) if (value > best) best = value; return best; }\n    public static void Main() {}\n}\n")
        );
    }

    private static final String FEEDBACK_JSON = """
            {
              "correctness": {"score": 8, "feedback": "The solution returns the expected answer for the main case and the obvious edges."},
              "optimality": {"score": 7, "feedback": "A single pass is the right shape. A couple of names could describe the invariant more clearly."},
              "timeComplexity": {"score": 8, "feedback": "One walk over the input.", "bigO": "O(n)"},
              "spaceComplexity": {"score": 7, "feedback": "Extra memory stays proportional to the distinct values seen.", "bigO": "O(n)"},
              "clarity": {"score": 8, "feedback": "The control flow is easy to follow."},
              "strengths": ["Handles the primary example", "Uses a linear scan"],
              "improvements": ["Name the intermediate values after the invariant they hold"],
              "overallFeedback": "This is a solid mock submission. The approach is correct and close to the reference solution.",
              "overallRating": 8
            }
            """;
}

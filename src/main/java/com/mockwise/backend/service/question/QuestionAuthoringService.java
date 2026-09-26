package com.mockwise.backend.service.question;

import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.repository.question.OptimalSolution;
import com.mockwise.backend.repository.question.OptimalSolutionRepository;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionCodeStub;
import com.mockwise.backend.repository.question.QuestionCodeStubRepository;
import com.mockwise.backend.repository.question.QuestionRepository;
import com.mockwise.backend.service.codesyntax.LanguageToolchainRegistry;
import com.mockwise.backend.service.codesyntax.SyntaxCheckFacade;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionAuthoringService {

    private final QuestionRepository questionRepository;
    private final QuestionCodeStubRepository questionCodeStubRepository;
    private final OptimalSolutionRepository optimalSolutionRepository;
    private final LanguageToolchainRegistry languageToolchainRegistry;
    private final SyntaxCheckFacade syntaxCheckFacade;

    @Transactional
    public Question create(NewQuestion request) {
        if (request == null) {
            throw new BadRequestException("Question details are required.");
        }
        List<String> problems = new ArrayList<>();
        String title = required(request.title(), "Title", problems);
        String description = required(request.description(), "Description", problems);
        String example = required(request.example(), "Example", problems);
        String constraints = required(request.constraints(), "Constraints", problems);
        Question.Difficulty difficulty = difficulty(request.difficulty(), problems);

        List<String> languages = languageToolchainRegistry.all().stream()
                .map(tool -> tool.languageId())
                .sorted()
                .toList();
        Map<String, String> stubs = index(request.stubs(), "code stub", languages, problems);
        Map<String, String> optimal = index(request.optimalSolutions(), "optimal code", languages, problems);
        if (!problems.isEmpty()) {
            throw new BadRequestException(String.join(" ", problems));
        }

        for (String language : languages) {
            rejectUnlessValid(language, "code stub", stubs.get(language), problems);
            rejectUnlessValid(language, "optimal code", optimal.get(language), problems);
        }
        if (!problems.isEmpty()) {
            throw new BadRequestException(String.join(" ", problems));
        }

        Question question = new Question();
        question.setTitle(title);
        question.setDescription(description);
        question.setExample(example);
        question.setConstraints(constraints);
        question.setDifficulty(difficulty);
        Question saved = questionRepository.save(question);

        Instant now = Instant.now();
        for (String language : languages) {
            QuestionCodeStub stub = new QuestionCodeStub();
            stub.setQuestion(saved);
            stub.setLanguage(language);
            stub.setStub(stubs.get(language));
            stub.setCreatedAt(now);
            questionCodeStubRepository.save(stub);

            OptimalSolution solution = new OptimalSolution();
            solution.setQuestion(saved);
            solution.setLanguage(language);
            solution.setCode(optimal.get(language));
            optimalSolutionRepository.save(solution);
        }
        return saved;
    }

    private void rejectUnlessValid(String language, String kind, String code, List<String> problems) {
        SyntaxCheckResult result = syntaxCheckFacade.check(language, code);
        if (!result.success()) {
            String detail = result.messages().isEmpty()
                    ? result.toolStatus().name()
                    : String.join(" ", result.messages());
            problems.add(language + " " + kind + " failed syntax check: " + detail);
        }
    }

    private Map<String, String> index(List<NewQuestion.LanguageCode> items,
                                      String kind,
                                      List<String> requiredLanguages,
                                      List<String> problems) {
        Map<String, String> byLanguage = new LinkedHashMap<>();
        if (items == null) {
            problems.add("Every supported language needs " + kind + ".");
            return byLanguage;
        }
        for (NewQuestion.LanguageCode item : items) {
            if (item == null || item.language() == null || item.language().isBlank()) {
                problems.add("A " + kind + " is missing its language.");
                continue;
            }
            String language = canonical(item.language());
            if (language == null) {
                problems.add("Unsupported language for " + kind + ": " + item.language().trim() + ".");
                continue;
            }
            if (byLanguage.containsKey(language)) {
                problems.add("Duplicate " + kind + " for " + language + ".");
                continue;
            }
            if (item.code() == null || item.code().isBlank()) {
                problems.add("The " + kind + " for " + language + " is empty.");
                continue;
            }
            byLanguage.put(language, item.code());
        }
        for (String language : requiredLanguages) {
            if (!byLanguage.containsKey(language)) {
                problems.add("Missing " + kind + " for " + language + ".");
            }
        }
        return byLanguage;
    }

    private String canonical(String raw) {
        return languageToolchainRegistry.find(raw.trim().toLowerCase(Locale.ROOT))
                .map(tool -> tool.languageId())
                .orElse(null);
    }

    private Question.Difficulty difficulty(String raw, List<String> problems) {
        if (raw == null || raw.isBlank()) {
            problems.add("Difficulty is required.");
            return null;
        }
        try {
            return Question.Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            problems.add("Difficulty must be EASY, MEDIUM, or HARD.");
            return null;
        }
    }

    private String required(String value, String label, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(label + " is required.");
            return "";
        }
        return value;
    }
}

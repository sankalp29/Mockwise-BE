package com.mockwise.backend.controller.question;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.controller.question.dto.QuestionResponse;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.service.question.OptimalSolutionService;
import com.mockwise.backend.service.question.QuestionService;
import com.mockwise.backend.service.question.QuestionStubService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionStubService questionStubService;
    private final OptimalSolutionService optimalSolutionService;

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> getQuestion(
            @PathVariable UUID questionId,
            Authentication authentication) {
        AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(QuestionResponse.from(questionService.getById(questionId)));
    }

    @GetMapping("/{questionId}/stub")
    public ResponseEntity<Map<String, String>> getCodeStub(
            @PathVariable UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        AuthSupport.requireUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        String stub = questionStubService.stubFor(questionId, language)
                .orElseThrow(() -> ResourceNotFoundException.of("Code stub for this language"));
        return ResponseEntity.ok(Map.of("stub", stub));
    }

    @GetMapping("/{questionId}/optimal-code")
    public ResponseEntity<Map<String, String>> getOptimalCode(
            @PathVariable UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        AuthSupport.requireUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        String code = optimalSolutionService.getOptimalCode(questionId, language);
        if (code == null || code.isBlank()) {
            throw ResourceNotFoundException.of("Optimal code");
        }
        return ResponseEntity.ok(Map.of("code", code));
    }
}

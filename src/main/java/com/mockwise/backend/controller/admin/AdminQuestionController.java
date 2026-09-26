package com.mockwise.backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.controller.admin.dto.CreateQuestionRequest;
import com.mockwise.backend.controller.question.dto.QuestionResponse;
import com.mockwise.backend.service.auth.UserAccountService;
import com.mockwise.backend.service.question.NewQuestion;
import com.mockwise.backend.service.question.QuestionAuthoringService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final UserAccountService userAccountService;
    private final QuestionAuthoringService questionAuthoringService;

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@RequestBody CreateQuestionRequest request,
                                                    Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        userAccountService.requireAdmin(user);
        return ResponseEntity.ok(QuestionResponse.from(questionAuthoringService.create(toNewQuestion(request))));
    }

    private static NewQuestion toNewQuestion(CreateQuestionRequest request) {
        if (request == null) {
            return null;
        }
        return new NewQuestion(
                request.title(),
                request.description(),
                request.example(),
                request.constraints(),
                request.difficulty(),
                codes(request.stubs()),
                codes(request.optimalSolutions()));
    }

    private static List<NewQuestion.LanguageCode> codes(List<CreateQuestionRequest.LanguageCode> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> item == null ? null : new NewQuestion.LanguageCode(item.language(), item.code()))
                .toList();
    }
}

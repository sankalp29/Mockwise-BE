package com.mockwise.backend.question.application;

import com.mockwise.backend.question.domain.OptimalSolution;
import com.mockwise.backend.question.infrastructure.OptimalSolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptimalSolutionService {

    private final OptimalSolutionRepository optimalSolutionRepository;

    public String getOptimalCode(UUID questionId, String language) {
        return optimalSolutionRepository
                .findByQuestionIdAndLanguage(questionId, language)
                .map(OptimalSolution::getCode)
                .orElse(null);
    }
}

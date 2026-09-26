package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.OptimalSolution;
import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.repository.question.OptimalSolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OptimalSolutionService {

    private final OptimalSolutionRepository optimalSolutionRepository;

    public String getOptimalCode(UUID questionId, ProgrammingLanguage language) {
        return optimalSolutionRepository
                .findByQuestionIdAndLanguage(questionId, language)
                .map(OptimalSolution::getCode)
                .orElse(null);
    }
}

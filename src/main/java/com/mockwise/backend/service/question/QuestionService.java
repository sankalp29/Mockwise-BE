package com.mockwise.backend.service.question;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    public Question getById(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question"));
    }
}

package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {
    
    List<InterviewQuestion> findByInterviewIdOrderByQuestionOrder(UUID interviewId);
    
    List<InterviewQuestion> findByQuestionId(UUID questionId);
}

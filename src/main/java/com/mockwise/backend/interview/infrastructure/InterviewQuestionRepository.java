package com.mockwise.backend.interview.infrastructure;

import com.mockwise.backend.interview.domain.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {

    List<InterviewQuestion> findByInterview_IdOrderByQuestionOrder(UUID interviewId);

    List<InterviewQuestion> findByQuestion_Id(UUID questionId);
}

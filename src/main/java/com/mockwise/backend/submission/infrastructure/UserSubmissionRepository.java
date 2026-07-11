package com.mockwise.backend.submission.infrastructure;

import com.mockwise.backend.submission.domain.UserSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSubmissionRepository extends JpaRepository<UserSubmission, UUID> {

    List<UserSubmission> findByInterviewId(UUID interviewId);

    @Query("SELECT s FROM UserSubmission s JOIN FETCH s.question WHERE s.interview.id = :interviewId")
    List<UserSubmission> findByInterviewIdWithQuestion(@Param("interviewId") UUID interviewId);

    List<UserSubmission> findByInterview_IdOrderBySubmittedAt(UUID interviewId);

    @Query("SELECT s FROM UserSubmission s JOIN FETCH s.question WHERE s.interview.id = :interviewId ORDER BY s.submittedAt")
    List<UserSubmission> findByInterviewIdWithQuestionOrderBySubmittedAt(@Param("interviewId") UUID interviewId);

    List<UserSubmission> findByInterviewIdIn(List<UUID> interviewIds);
}

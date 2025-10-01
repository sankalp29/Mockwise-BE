package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSubmissionRepository extends JpaRepository<UserSubmission, UUID> {
    
    List<UserSubmission> findByInterviewId(UUID interviewId);
    
    List<UserSubmission> findByInterview_IdOrderBySubmittedAt(UUID interviewId);
    
    // OPTIMIZATION: Batch query for multiple interview IDs
    List<UserSubmission> findByInterviewIdIn(List<UUID> interviewIds);
}

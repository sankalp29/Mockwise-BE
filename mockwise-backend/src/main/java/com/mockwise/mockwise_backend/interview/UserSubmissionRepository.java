package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSubmissionRepository extends JpaRepository<UserSubmission, UUID> {
    
    // Use nested property resolution on the association field 'interview'
    List<UserSubmission> findByInterview_Id(UUID interviewId);
    
    List<UserSubmission> findByInterview_IdOrderBySubmittedAt(UUID interviewId);
}

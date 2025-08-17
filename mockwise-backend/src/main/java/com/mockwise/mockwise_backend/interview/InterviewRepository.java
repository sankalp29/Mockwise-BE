package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    
    List<Interview> findByUserIdOrderByStartedAtDesc(String userId);
    
    List<Interview> findByUserIdAndStatus(String userId, Interview.Status status);
}

package com.mockwise.backend.repository.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {

    List<PracticeResult> findByUserIdOrderByStartedAtDesc(String userId);

    List<PracticeResult> findByUserIdAndPracticeOrderByStartedAtDesc(String userId, Practice practice);

    Optional<PracticeResult> findByPracticeAndSourceKey(Practice practice, UUID sourceKey);
}

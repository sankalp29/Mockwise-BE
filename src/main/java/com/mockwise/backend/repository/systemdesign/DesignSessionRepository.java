package com.mockwise.backend.repository.systemdesign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignSessionRepository extends JpaRepository<DesignSession, UUID> {

    Optional<DesignSession> findFirstByUserIdAndStatus(String userId, SessionStatus status);

    List<DesignSession> findByUserIdAndStatus(String userId, SessionStatus status);
}

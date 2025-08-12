package com.mockwise.mockwise_backend.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByToken(String token);
    long deleteByConsumedAtBefore(Instant cutoff);
    long deleteByExpiresAtBefore(Instant cutoff);
    void deleteByUser(User user);
}



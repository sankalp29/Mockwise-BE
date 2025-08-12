package com.mockwise.mockwise_backend.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VerificationTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(VerificationTokenCleanupJob.class);
    private final VerificationTokenRepository repository;

    public VerificationTokenCleanupJob(VerificationTokenRepository repository) {
        this.repository = repository;
    }

    // Run daily at 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldTokens() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long deletedConsumed = repository.deleteByConsumedAtBefore(sevenDaysAgo);
        long deletedExpired = repository.deleteByExpiresAtBefore(sevenDaysAgo);
        if (deletedConsumed + deletedExpired > 0) {
            log.info("VerificationToken cleanup: deleted consumed={} expired={}", deletedConsumed, deletedExpired);
        }
    }
}



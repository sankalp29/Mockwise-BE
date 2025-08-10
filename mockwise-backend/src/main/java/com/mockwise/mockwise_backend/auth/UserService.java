package com.mockwise.mockwise_backend.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final int expiryHours;
    private final String frontendBaseUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository,
                       VerificationTokenRepository tokenRepository,
                       EmailService emailService,
                       @Value("${app.verification.expiry-hours:3}") int expiryHours,
                       @Value("${app.frontend-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.expiryHours = expiryHours;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void registerLocalUser(String name, String email, String rawPassword) {
        String emailLower = email.toLowerCase();
        if (userRepository.findByEmailLower(emailLower).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(name, email, hash, null);
        userRepository.save(user);

        // create and email verification token
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS);
        tokenRepository.save(new VerificationToken(user, token, expiresAt));
        sendVerificationEmail(user.getEmail(), user.getName(), token);
    }

    public void sendVerificationEmail(String email, String name, String token) {
        String link = frontendBaseUrl + "/verify-email/confirm?token=" + token;
        emailService.sendVerificationEmail(email, name, link);
    }

    public enum VerifyOutcome { VERIFIED, ALREADY_VERIFIED }

    @Transactional
    public VerifyOutcome verifyEmail(String token) {
        VerificationToken vt = tokenRepository.findByToken(token)
                .orElseThrow(TokenInvalidException::new);
        User user = vt.getUser();
        // Idempotent behavior: if user is already verified, return already verified
        if (user.isEmailVerified()) {
            return VerifyOutcome.ALREADY_VERIFIED;
        }
        // If this token was already consumed, treat as already verified (resends, double-click)
        if (vt.getConsumedAt() != null) {
            return VerifyOutcome.ALREADY_VERIFIED;
        }
        if (vt.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException();
        }

        user.setEmailVerified(true);
        vt.setConsumedAt(Instant.now());
        return VerifyOutcome.VERIFIED;
    }

    public static class EmailAlreadyExistsException extends RuntimeException {}
    public static class TokenInvalidException extends RuntimeException {}
    public static class TokenExpiredException extends RuntimeException {}
}



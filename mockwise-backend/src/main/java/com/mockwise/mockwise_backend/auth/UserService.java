package com.mockwise.mockwise_backend.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final int expiryHours;
    private final String frontendBaseUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository,
                       VerificationTokenRepository tokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService,
                       @Value("${app.verification.expiry-hours:3}") int expiryHours,
                       @Value("${app.frontend-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
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
        log.info("Created verification token for email={} exp={}h", emailLower, expiryHours);
        sendVerificationEmail(user.getEmail(), user.getName(), token);
    }

    public void sendVerificationEmail(String email, String name, String token) {
        String link = frontendBaseUrl + "/verify-email/confirm?token=" + token;
        emailService.sendVerificationEmail(email, name, link);
    }

    public enum LoginOutcome { OK, NOT_FOUND, NOT_VERIFIED, INVALID_PASSWORD }

    public LoginOutcome validateLogin(String email, String rawPassword) {
        String emailLower = email.toLowerCase();
        var userOpt = userRepository.findByEmailLower(emailLower);
        if (userOpt.isEmpty()) {
            log.warn("Login attempt for unknown email={}", emailLower);
            return LoginOutcome.NOT_FOUND;
        }
        var user = userOpt.get();
        if (!user.isEmailVerified()) {
            log.warn("Login attempt for unverified email={}", emailLower);
            return LoginOutcome.NOT_VERIFIED;
        }
        boolean matches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        log.info("Password match for email={} -> {}", emailLower, matches);
        return matches ? LoginOutcome.OK : LoginOutcome.INVALID_PASSWORD;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String emailLower = email.toLowerCase();
        var userOpt = userRepository.findByEmailLower(emailLower);
        if (userOpt.isEmpty()) {
            throw new UnknownEmailException();
        }
        var user = userOpt.get();
        // Invalidate previous reset tokens
        passwordResetTokenRepository.deleteByUser(user);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS);
        passwordResetTokenRepository.save(new PasswordResetToken(user, token, expiresAt));
        String link = frontendBaseUrl + "/reset-password?token=" + token;
        emailService.sendEmail(user.getEmail(), "Reset your password", "Click to reset: " + link, false);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(TokenInvalidException::new);
        if (prt.getConsumedAt() != null || prt.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException();
        }
        var user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        prt.setConsumedAt(Instant.now());
        // Remove any other outstanding tokens for this user to prevent reuse
        passwordResetTokenRepository.deleteByUser(user);
    }

    public static class UnknownEmailException extends RuntimeException {}

    public enum VerifyOutcome { VERIFIED, ALREADY_VERIFIED }

    public enum ResetTokenStatus { VALID, EXPIRED, USED, INVALID }

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

    public ResetTokenStatus checkPasswordResetToken(String token) {
        var opt = passwordResetTokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return ResetTokenStatus.INVALID;
        }
        var prt = opt.get();
        if (prt.getConsumedAt() != null) {
            return ResetTokenStatus.USED;
        }
        if (prt.getExpiresAt().isBefore(Instant.now())) {
            return ResetTokenStatus.EXPIRED;
        }
        return ResetTokenStatus.VALID;
    }

    @Transactional
    public void resendVerificationUsingExpiredToken(String token) {
        VerificationToken vt = tokenRepository.findByToken(token).orElseThrow(TokenInvalidException::new);
        User user = vt.getUser();
        if (user.isEmailVerified()) {
            return; // no-op if already verified
        }
        // Clean up old tokens for the user
        tokenRepository.deleteByUser(user);
        // Issue a fresh token with current expiry window
        String newToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS);
        tokenRepository.save(new VerificationToken(user, newToken, expiresAt));
        sendVerificationEmail(user.getEmail(), user.getName(), newToken);
    }

    public static class EmailAlreadyExistsException extends RuntimeException {}
    public static class TokenInvalidException extends RuntimeException {}
    public static class TokenExpiredException extends RuntimeException {}
}



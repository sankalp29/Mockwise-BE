package com.mockwise.mockwise_backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final RememberMeServices rememberMeServices;

    public AuthController(UserService userService, AuthenticationManager authenticationManager, RememberMeServices rememberMeServices) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.rememberMeServices = rememberMeServices;
    }


    public record SignupRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    /** Signup: returns 201 on success, 409 if email exists */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            log.info("Signup request for {}", request.email().toLowerCase());
            userService.registerLocalUser(request.name(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (UserService.EmailAlreadyExistsException ex) {
            log.warn("Signup conflict for {}", request.email().toLowerCase());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already registered"));
        }
    }

    /** Verify email by token */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        try {
            log.info("Verify token {}", token);
            var outcome = userService.verifyEmail(token);
            if (outcome == UserService.VerifyOutcome.ALREADY_VERIFIED) {
                log.info("Token already verified {}", token);
                return ResponseEntity.ok(Map.of("message", "Email already verified. Please login."));
            }
            log.info("Token verified {}", token);
            return ResponseEntity.ok(Map.of("message", "Email verified. You can now login."));
        } catch (UserService.TokenInvalidException e) {
            log.warn("Token invalid {}", token);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid token"));
        } catch (UserService.TokenExpiredException e) {
            log.warn("Token expired {}", token);
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", "Verification link expired"));
        }
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank @Size(min=8,max=100) String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest, jakarta.servlet.http.HttpServletResponse httpResponse) {
        log.info("Login attempt {}", request.email().toLowerCase());
        var outcome = userService.validateLogin(request.email(), request.password());
        switch (outcome) {
            case NOT_FOUND:
                log.warn("Login NOT_FOUND {}", request.email().toLowerCase());
                return ResponseEntity.status(402).body(Map.of("message", "Email not registered"));
            case NOT_VERIFIED:
                log.warn("Login NOT_VERIFIED {}", request.email().toLowerCase());
                return ResponseEntity.badRequest().body(Map.of("message", "Email not verified"));
            case INVALID_PASSWORD:
                log.warn("Login INVALID_PASSWORD {}", request.email().toLowerCase());
                return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
            case OK:
                try {
                    Authentication auth = authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    httpRequest.getSession(true); // ensure session creation
                    log.info("Login OK principal={} sessionId={} ", auth.getName(), httpRequest.getSession(false).getId());
                    rememberMeServices.loginSuccess(httpRequest, httpResponse, auth);
                    return ResponseEntity.ok().build();
                } catch (AuthenticationException ex) {
                    log.error("AuthenticationManager rejected {} : {}", request.email().toLowerCase(), ex.getMessage());
                    return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
                }
            default:
                return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/password/reset-request")
    public ResponseEntity<?> passwordResetRequest(@Valid @RequestBody PasswordResetRequest request) {
        try {
            userService.requestPasswordReset(request.email());
            return ResponseEntity.ok(Map.of("message", "Password reset email sent"));
        } catch (UserService.UnknownEmailException e) {
            return ResponseEntity.status(402).body(Map.of("message", "Email not registered"));
        }
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> passwordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        try {
            userService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(Map.of("message", "Password has been reset"));
        } catch (UserService.TokenInvalidException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid token"));
        } catch (UserService.TokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", "Reset link expired"));
        }
    }

    @GetMapping("/password/reset/validate")
    public ResponseEntity<?> validateResetToken(@RequestParam("token") String token) {
        var status = userService.checkPasswordResetToken(token);
        return switch (status) {
            case VALID -> ResponseEntity.ok(Map.of("status", "valid"));
            case USED -> ResponseEntity.status(HttpStatus.GONE).body(Map.of("status", "used"));
            case EXPIRED -> ResponseEntity.status(HttpStatus.GONE).body(Map.of("status", "expired"));
            case INVALID -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "invalid"));
        };
    }

    @PostMapping("/verify/resend")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing token"));
        }
        try {
            userService.resendVerificationUsingExpiredToken(token);
            return ResponseEntity.ok(Map.of("message", "A new verification email has been sent"));
        } catch (UserService.TokenInvalidException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid verification token"));
        }
    }

    /**
     * Returns details of the currently authenticated user.
     * Endpoint: GET /api/auth/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getCurrentUserProfile(Authentication authentication,
                                                                     @AuthenticationPrincipal OAuth2User oauth2Principal) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        // If OAuth2 user, return provider attributes
        if (oauth2Principal != null) {
            return ResponseEntity.ok(oauth2Principal.getAttributes());
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserDetails details) {
            var user = details.getDomainUser();
            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail(),
                    "provider", "local"
            ));
        }

        // Fallback: minimal info
        return ResponseEntity.ok(Map.of("username", String.valueOf(authentication.getName())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}

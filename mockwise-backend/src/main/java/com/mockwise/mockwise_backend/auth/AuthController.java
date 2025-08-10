package com.mockwise.mockwise_backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** Signup: returns 201 on success, 409 if email exists */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            userService.registerLocalUser(request.name(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (UserService.EmailAlreadyExistsException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already registered"));
        }
    }
    
    /**
     * Returns details of the currently authenticated user.
     * Endpoint: GET /api/auth/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getCurrentUserProfile(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> userAttributes = principal.getAttributes();
        return ResponseEntity.ok(userAttributes);
    }

    /** Verify email by token */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        try {
            var outcome = userService.verifyEmail(token);
            if (outcome == UserService.VerifyOutcome.ALREADY_VERIFIED) {
                return ResponseEntity.ok(Map.of("message", "Email already verified. Please login."));
            }
            return ResponseEntity.ok(Map.of("message", "Email verified. You can now login."));
        } catch (UserService.TokenInvalidException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid token"));
        } catch (UserService.TokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", "Verification link expired"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    public record SignupRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}
}

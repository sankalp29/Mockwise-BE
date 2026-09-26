package com.mockwise.backend.controller.account;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.repository.auth.AppUser;
import com.mockwise.backend.service.auth.UserAccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserAccountService userAccountService;

    /**
     * Called after signup and on later sign-in. A new email becomes USER.
     * The seeded admin email stays ADMIN.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> ensure(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        AppUser account = userAccountService.ensure(user);
        return ResponseEntity.ok(Map.of(
                "email", account.getEmail(),
                "role", account.getRole().name()
        ));
    }
}

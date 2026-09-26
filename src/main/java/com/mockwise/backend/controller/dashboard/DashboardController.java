package com.mockwise.backend.controller.dashboard;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.repository.dashboard.Practice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ResponseEntity<?> getDashboardData(
            @RequestParam(required = false) String practice,
            Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(dashboardService.metricsFor(user.getId(), parsePractice(practice)));
    }

    @GetMapping("/progress")
    public ResponseEntity<?> getProgress(
            @RequestParam(required = false) String practice,
            Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(dashboardService.historyFor(user.getId(), parsePractice(practice)));
    }

    private static Practice parsePractice(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Practice.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Practice must be ALL, CODING, or SYSTEM_DESIGN.");
        }
    }
}

package com.mockwise.backend.controller.systemdesign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.controller.systemdesign.dto.StartDesignRequest;
import com.mockwise.backend.controller.systemdesign.dto.SubmitDesignRequest;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.repository.systemdesign.DesignBoard;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.service.systemdesign.DesignScene;
import com.mockwise.backend.service.systemdesign.DesignSessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/system-design/sessions")
@RequiredArgsConstructor
public class DesignSessionController {

    private final DesignSessionService designSessionService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartDesignRequest request,
                                                     Authentication authentication) {
        if (request == null) {
            throw new BadRequestException("Difficulty and time are required.");
        }
        SupabaseUser user = AuthSupport.requireUser(authentication);
        if (request.getDifficulty() == null) {
            throw new BadRequestException("Difficulty is required.");
        }
        DesignSession session = designSessionService.start(
                user,
                request.getDifficulty(),
                request.getTimeMinutes() == null ? 0 : request.getTimeMinutes());
        return ResponseEntity.ok(openView(session, remaining(session)));
    }

    @GetMapping("/{sessionKey}")
    public ResponseEntity<Map<String, Object>> load(@PathVariable UUID sessionKey,
                                                    Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        DesignSession session = designSessionService.requireOpen(sessionKey, user.getId());
        return ResponseEntity.ok(openView(session, remaining(session)));
    }

    @PostMapping("/{sessionKey}/submit")
    public ResponseEntity<Map<String, String>> submit(@PathVariable UUID sessionKey,
                                                      @RequestBody SubmitDesignRequest request,
                                                      Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        if (request == null) {
            throw new BadRequestException("Scene is required.");
        }
        DesignScene scene = DesignScene.require(request.getScene(), objectMapper);
        DesignSession session = designSessionService.submit(sessionKey, user.getId(), scene.json(), request.getCaption());
        return ResponseEntity.ok(Map.of(
                "message", "Design session submitted",
                "sessionKey", session.getId().toString()
        ));
    }

    @GetMapping("/{sessionKey}/feedback")
    public ResponseEntity<Map<String, Object>> feedback(@PathVariable UUID sessionKey,
                                                        Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        DesignBoard board = designSessionService.feedback(sessionKey, user.getId());
        DesignSession session = board.getSession();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", sessionView(session));
        body.put("prompt", promptView(session.getPrompt()));
        body.put("scene", sceneView(board.getScene()));
        body.put("caption", board.getCaption() == null ? "" : board.getCaption());
        body.put("review", board.getReview());
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> openView(DesignSession session, long remainingMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", sessionView(session));
        body.put("prompt", promptView(session.getPrompt()));
        body.put("remainingMs", remainingMs);
        return body;
    }

    private static Map<String, Object> sessionView(DesignSession session) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", session.getId());
        view.put("difficulty", session.getLevel().name());
        view.put("timeMinutes", session.getTimeMinutes());
        view.put("startedAt", session.getStartedAt());
        view.put("status", session.getStatus().name());
        view.put("overallRating", session.getOverallRating());
        return view;
    }

    private static Map<String, Object> promptView(DesignPrompt prompt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", prompt.getId());
        view.put("title", prompt.getTitle());
        view.put("brief", prompt.getBrief());
        view.put("requirements", prompt.getRequirements());
        view.put("constraints", prompt.getConstraints());
        view.put("difficulty", prompt.getLevel().name());
        return view;
    }

    private Object sceneView(String stored) {
        JsonNode tree = DesignScene.tree(stored, objectMapper);
        return tree == null ? stored : tree;
    }

    private static long remaining(DesignSession session) {
        long elapsed = System.currentTimeMillis() - session.getStartedAt().toEpochMilli();
        long budget = session.getTimeMinutes() * 60_000L;
        return Math.max(0, budget - elapsed);
    }

}

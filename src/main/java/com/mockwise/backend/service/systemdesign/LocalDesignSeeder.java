package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.repository.systemdesign.DesignBoard;
import com.mockwise.backend.repository.systemdesign.DesignBoardRepository;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.repository.systemdesign.DesignPromptRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Profile("local")
@Order(20)
@RequiredArgsConstructor
public class LocalDesignSeeder implements ApplicationRunner {

    private final DesignPromptRepository promptRepository;
    private final DesignSessionRepository sessionRepository;
    private final DesignBoardRepository boardRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DesignCatalog.PromptDraft draft : DesignCatalog.prompts()) {
            DesignPrompt prompt = promptRepository.findFirstByTitleIgnoreCase(draft.title()).orElseGet(DesignPrompt::new);
            prompt.setTitle(draft.title());
            prompt.setLevel(draft.level());
            prompt.setBrief(draft.brief());
            prompt.setRequirements(draft.requirements());
            prompt.setConstraints(draft.constraints());
            promptRepository.save(prompt);
        }
        seedCompletedSample();
    }

    private void seedCompletedSample() {
        boolean present = sessionRepository
                .findFirstByUserIdAndStatus("bypass-test-user", SessionStatus.COMPLETED)
                .isPresent();
        if (present) {
            return;
        }
        DesignPrompt prompt = promptRepository.findFirstByTitleIgnoreCase("URL Shortener").orElse(null);
        if (prompt == null) {
            return;
        }
        DesignSession session = new DesignSession();
        session.setUserId("bypass-test-user");
        session.setUserEmail("test@mockwise.local");
        session.setLevel(Difficulty.EASY);
        session.setTimeMinutes(45);
        session.setPrompt(prompt);
        session.setStartedAt(Instant.now().minusSeconds(2400));
        session.setEndedAt(Instant.now().minusSeconds(300));
        session.setStatus(SessionStatus.COMPLETED);
        session.setOverallRating(8.0);
        DesignSession saved = sessionRepository.save(session);

        DesignBoard board = new DesignBoard();
        board.setSession(saved);
        board.setScene(DesignCatalog.sampleScene());
        board.setCaption("Client calls the API, which stores the mapping and serves redirects from a cache.");
        board.setReview(DesignCatalog.sampleReview());
        board.setSubmittedAt(saved.getEndedAt());
        boardRepository.save(board);
    }
}

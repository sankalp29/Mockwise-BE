package com.mockwise.backend.repository.systemdesign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignPromptRepository extends JpaRepository<DesignPrompt, UUID> {

    List<DesignPrompt> findByLevel(Level level);

    Optional<DesignPrompt> findFirstByTitleIgnoreCase(String title);
}

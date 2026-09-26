package com.mockwise.backend.repository.systemdesign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DesignBoardRepository extends JpaRepository<DesignBoard, UUID> {

    Optional<DesignBoard> findBySession_Id(UUID sessionKey);
}

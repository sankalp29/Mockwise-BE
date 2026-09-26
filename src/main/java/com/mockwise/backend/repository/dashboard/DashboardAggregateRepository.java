package com.mockwise.backend.repository.dashboard;

import com.mockwise.backend.repository.dashboard.DashboardAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardAggregateRepository extends JpaRepository<DashboardAggregate, UUID> {
    Optional<DashboardAggregate> findByUserId(String userId);
}

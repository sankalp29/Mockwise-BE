package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardAggregateRepository extends JpaRepository<DashboardAggregate, UUID> {
    Optional<DashboardAggregate> findByUserId(String userId);
}



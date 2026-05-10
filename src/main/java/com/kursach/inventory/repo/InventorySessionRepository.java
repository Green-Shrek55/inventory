package com.kursach.inventory.repo;

import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.InventorySessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventorySessionRepository extends JpaRepository<InventorySession, Long> {
    Optional<InventorySession> findFirstByStatusOrderByStartedAtAsc(InventorySessionStatus status);

    boolean existsByStatus(InventorySessionStatus status);

    boolean existsByLocation_IdAndStatus(Long locationId, InventorySessionStatus status);

    List<InventorySession> findByLocation_Id(Long locationId);

    List<InventorySession> findTop10ByOrderByStartedAtDesc();
}

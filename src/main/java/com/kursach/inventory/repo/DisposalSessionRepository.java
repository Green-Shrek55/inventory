package com.kursach.inventory.repo;

import com.kursach.inventory.domain.DisposalSession;
import com.kursach.inventory.domain.InventorySessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisposalSessionRepository extends JpaRepository<DisposalSession, Long> {
    Optional<DisposalSession> findFirstByStatusOrderByStartedAtAsc(InventorySessionStatus status);

    Optional<DisposalSession> findFirstByBuilding_IdAndStatusOrderByStartedAtAsc(Long buildingId, InventorySessionStatus status);

    boolean existsByStatus(InventorySessionStatus status);

    List<DisposalSession> findTop10ByOrderByStartedAtDesc();
}

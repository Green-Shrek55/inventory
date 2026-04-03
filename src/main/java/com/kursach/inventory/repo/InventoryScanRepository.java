package com.kursach.inventory.repo;

import com.kursach.inventory.domain.InventoryScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryScanRepository extends JpaRepository<InventoryScan, Long> {
    List<InventoryScan> findBySessionIdOrderByScannedAtAsc(Long sessionId);
}

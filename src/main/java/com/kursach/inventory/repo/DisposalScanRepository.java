package com.kursach.inventory.repo;

import com.kursach.inventory.domain.DisposalScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisposalScanRepository extends JpaRepository<DisposalScan, Long> {
    List<DisposalScan> findBySession_IdOrderByScannedAtAsc(Long sessionId);

    Optional<DisposalScan> findBySession_IdAndEquipment_Id(Long sessionId, Long equipmentId);
}

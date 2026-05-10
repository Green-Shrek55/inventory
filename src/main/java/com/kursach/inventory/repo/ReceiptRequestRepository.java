package com.kursach.inventory.repo;

import com.kursach.inventory.domain.ReceiptRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptRequestRepository extends JpaRepository<ReceiptRequest, Long> {
    List<ReceiptRequest> findAllByOrderByCreatedAtDesc();
    List<ReceiptRequest> findByBuilding_IdOrderByCreatedAtDesc(Long buildingId);
}

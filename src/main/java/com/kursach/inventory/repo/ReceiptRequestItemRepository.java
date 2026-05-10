package com.kursach.inventory.repo;

import com.kursach.inventory.domain.ReceiptRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptRequestItemRepository extends JpaRepository<ReceiptRequestItem, Long> {
    Optional<ReceiptRequestItem> findByRequest_IdAndExpectedInventoryNumberIgnoreCase(Long requestId, String expectedInventoryNumber);
}

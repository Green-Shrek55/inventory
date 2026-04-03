package com.kursach.inventory.repo;

import com.kursach.inventory.domain.EquipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, Long> {
    List<EquipmentItem> findByArchivedFalseOrderByInventoryNumberAsc();
    List<EquipmentItem> findByArchivedTrueOrderByArchivedAtDesc();
    List<EquipmentItem> findByLocation_NameIgnoreCaseAndArchivedFalseOrderByInventoryNumberAsc(String name);
    List<EquipmentItem> findByLocation_IdAndArchivedFalseOrderByInventoryNumberAsc(Long locationId);
    Optional<EquipmentItem> findByInventoryNumber(String inventoryNumber);

    @Query("""
            select e from EquipmentItem e
            where e.archived = true
              and (:fromTs is null or e.archivedAt >= :fromTs)
              and (:toTs is null or e.archivedAt < :toTs)
            order by e.archivedAt desc
            """)
    List<EquipmentItem> findArchivedBetween(@Param("fromTs") Instant fromTs,
                                           @Param("toTs") Instant toTs);
}

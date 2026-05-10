package com.kursach.inventory.repo;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, Long> {
    List<EquipmentItem> findByArchivedFalseOrderByInventoryNumberAsc();
    List<EquipmentItem> findByLocation_Building_IdAndArchivedFalseOrderByInventoryNumberAsc(Long buildingId);
    List<EquipmentItem> findByArchivedTrueOrderByArchivedAtDesc();
    List<EquipmentItem> findByLocation_Building_IdAndArchivedTrueOrderByArchivedAtDesc(Long buildingId);
    List<EquipmentItem> findByLocation_TypeAndArchivedFalseOrderByInventoryNumberAsc(LocationType type);
    List<EquipmentItem> findByLocation_Building_IdAndLocation_TypeAndArchivedFalseOrderByInventoryNumberAsc(Long buildingId, LocationType type);
    List<EquipmentItem> findByLocation_IdAndLocation_TypeAndArchivedFalseOrderByInventoryNumberAsc(Long locationId, LocationType type);
    List<EquipmentItem> findByLocation_IdAndArchivedFalseOrderByInventoryNumberAsc(Long locationId);
    List<EquipmentItem> findByLocation_Id(Long locationId);
    boolean existsByLocation_Id(Long locationId);
    List<EquipmentItem> findByArchivedFalseAndPurchaseDateLessThanEqualOrderByPurchaseDateAscInventoryNumberAsc(LocalDate date);
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

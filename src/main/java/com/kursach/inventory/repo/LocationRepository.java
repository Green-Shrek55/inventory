package com.kursach.inventory.repo;

import com.kursach.inventory.domain.Location;
import com.kursach.inventory.domain.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByTypeOrderByBuilding_NameAscNameAsc(LocationType type);
    List<Location> findByBuilding_IdOrderByTypeAscNameAsc(Long buildingId);
    boolean existsByBuilding_IdAndNameIgnoreCaseAndType(Long buildingId, String name, LocationType type);
}

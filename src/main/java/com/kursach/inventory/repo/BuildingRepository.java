package com.kursach.inventory.repo;

import com.kursach.inventory.domain.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findAllByOrderByNameAsc();
    Optional<Building> findByNameIgnoreCase(String name);
}

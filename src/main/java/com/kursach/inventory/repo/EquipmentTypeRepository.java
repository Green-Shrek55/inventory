package com.kursach.inventory.repo;

import com.kursach.inventory.domain.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {}

package com.kursach.inventory.repo;

import com.kursach.inventory.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {}

package com.kursach.inventory.repo;

import com.kursach.inventory.domain.ActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
    Page<ActionLog> findByActorContainingIgnoreCaseAndMessageContainingIgnoreCase(
            String actor,
            String message,
            Pageable pageable);

    @Query("select distinct l.actor from ActionLog l order by l.actor asc")
    java.util.List<String> findDistinctActors();
}

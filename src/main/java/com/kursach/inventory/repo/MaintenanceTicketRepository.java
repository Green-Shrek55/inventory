package com.kursach.inventory.repo;

import com.kursach.inventory.domain.MaintenanceStatus;
import com.kursach.inventory.domain.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, Long> {
    List<MaintenanceTicket> findByStatusInOrderByUpdatedAtDesc(Collection<MaintenanceStatus> statuses);
    List<MaintenanceTicket> findTop10ByOrderByUpdatedAtDesc();
}

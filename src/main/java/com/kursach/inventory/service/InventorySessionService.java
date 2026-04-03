package com.kursach.inventory.service;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.InventoryScan;
import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.InventorySessionStatus;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.repo.InventoryScanRepository;
import com.kursach.inventory.repo.InventorySessionRepository;
import com.kursach.inventory.repo.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class InventorySessionService {

    private final InventorySessionRepository repository;
    private final ActionLogService actionLogService;
    private final InventoryScanRepository scanRepository;
    private final LocationRepository locationRepository;

    public InventorySessionService(InventorySessionRepository repository,
                                   ActionLogService actionLogService,
                                   InventoryScanRepository scanRepository,
                                   LocationRepository locationRepository) {
        this.repository = repository;
        this.actionLogService = actionLogService;
        this.scanRepository = scanRepository;
        this.locationRepository = locationRepository;
    }

    public Optional<InventorySession> findActive() {
        return repository.findFirstByStatusOrderByStartedAtAsc(InventorySessionStatus.ACTIVE);
    }

    public List<InventorySession> recentSessions() {
        return repository.findTop10ByOrderByStartedAtDesc();
    }

    public Optional<InventorySession> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public InventorySession startSession(String actor, Long locationId) {
        if (locationId == null) {
            throw new IllegalArgumentException("Укажите место проведения инвентаризации");
        }
        if (repository.existsByStatus(InventorySessionStatus.ACTIVE)) {
            throw new IllegalStateException("Уже есть активная сессия инвентаризации");
        }
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Локация не найдена"));
        InventorySession session = new InventorySession();
        session.setStartedBy(actor);
        session.setStartedAt(Instant.now());
        session.setLocation(location);
        InventorySession saved = repository.save(session);
        actionLogService.log(actor, "Инвентаризация: начата сессия #" + saved.getId()
                + " (" + location.getName() + ")");
        return saved;
    }

    @Transactional
    public InventorySession finishActiveSession(String actor) {
        InventorySession session = repository.findFirstByStatusOrderByStartedAtAsc(InventorySessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Открытых сессий нет"));
        session.setStatus(InventorySessionStatus.COMPLETED);
        session.setFinishedAt(Instant.now());
        session.setFinishedBy(actor);
        InventorySession saved = repository.save(session);
        String locationName = saved.getLocation() != null ? saved.getLocation().getName() : "-";
        actionLogService.log(actor, "Инвентаризация: завершена сессия #" + saved.getId()
                + " (" + locationName + ")"
                + ", отсканировано " + saved.getScannedCount() + " ед.");
        return saved;
    }

    @Transactional
    public void registerScan(String actor, EquipmentItem item) {
        InventorySession session = repository.findFirstByStatusOrderByStartedAtAsc(InventorySessionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Сначала начните сессию инвентаризации"));
        Location sessionLocation = session.getLocation();
        if (sessionLocation == null) {
            throw new IllegalStateException("Для сессии не указана локация");
        }
        Location itemLocation = item.getLocation();
        if (itemLocation == null || itemLocation.getId() == null
                || !itemLocation.getId().equals(sessionLocation.getId())) {
            String itemLocName = itemLocation != null ? itemLocation.getName() : "не определено";
            throw new IllegalStateException("Текущая сессия проходит в "
                    + sessionLocation.getName() + ", а оборудование находится в " + itemLocName);
        }
        session.incrementScans();
        repository.save(session);

        InventoryScan scan = new InventoryScan();
        scan.setSession(session);
        scan.setEquipment(item);
        scan.setInventoryNumber(item.getInventoryNumber());
        scan.setEquipmentName(item.getName());
        scan.setLocationName(item.getLocation() != null ? item.getLocation().getName() : "-");
        scan.setTypeName(item.getType() != null ? item.getType().getName() : "-");
        scan.setAssignedPerson(item.getAssignedTo() != null ? item.getAssignedTo().getFullName() : "-");
        scanRepository.save(scan);
    }

    public List<InventoryScan> findScans(Long sessionId) {
        return scanRepository.findBySessionIdOrderByScannedAtAsc(sessionId);
    }
}

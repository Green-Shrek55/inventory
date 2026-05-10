package com.kursach.inventory.service;

import com.kursach.inventory.domain.Building;
import com.kursach.inventory.domain.DisposalScan;
import com.kursach.inventory.domain.DisposalSession;
import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.InventorySessionStatus;
import com.kursach.inventory.repo.BuildingRepository;
import com.kursach.inventory.repo.DisposalScanRepository;
import com.kursach.inventory.repo.DisposalSessionRepository;
import com.kursach.inventory.repo.EquipmentItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DisposalSessionService {
    private final DisposalSessionRepository sessionRepository;
    private final DisposalScanRepository scanRepository;
    private final EquipmentItemRepository equipmentRepository;
    private final BuildingRepository buildingRepository;
    private final ActionLogService logService;

    public DisposalSessionService(DisposalSessionRepository sessionRepository,
                                  DisposalScanRepository scanRepository,
                                  EquipmentItemRepository equipmentRepository,
                                  BuildingRepository buildingRepository,
                                  ActionLogService logService) {
        this.sessionRepository = sessionRepository;
        this.scanRepository = scanRepository;
        this.equipmentRepository = equipmentRepository;
        this.buildingRepository = buildingRepository;
        this.logService = logService;
    }

    public Optional<DisposalSession> findActive(Long buildingId) {
        if (buildingId == null) {
            return Optional.empty();
        }
        return sessionRepository.findFirstByBuilding_IdAndStatusOrderByStartedAtAsc(buildingId, InventorySessionStatus.ACTIVE);
    }

    public List<DisposalSession> recentSessions() {
        return sessionRepository.findTop10ByOrderByStartedAtDesc();
    }

    public Optional<DisposalSession> findById(Long id) {
        return sessionRepository.findById(id);
    }

    public List<DisposalScan> findScans(Long sessionId) {
        return scanRepository.findBySession_IdOrderByScannedAtAsc(sessionId);
    }

    @Transactional
    public DisposalSession startSession(Long buildingId, String actor) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Выберите корпус перед началом утилизации");
        }
        if (sessionRepository.existsByStatus(InventorySessionStatus.ACTIVE)) {
            throw new IllegalStateException("Уже есть активная сессия утилизации. Завершите ее перед запуском новой.");
        }
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));
        DisposalSession session = new DisposalSession();
        session.setBuilding(building);
        session.setStartedBy(actor);
        DisposalSession saved = sessionRepository.save(session);
        logService.log(actor, "Начата сессия утилизации № " + saved.getId());
        return saved;
    }

    @Transactional
    public DisposalScan scan(Long buildingId, String code, String actor) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Выберите корпус перед сканированием");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Введите или просканируйте инвентарный номер");
        }
        DisposalSession session = findActive(buildingId)
                .orElseThrow(() -> new IllegalStateException("Сначала начните сессию утилизации"));
        EquipmentItem item = equipmentRepository.findByInventoryNumber(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Оборудование с таким номером не найдено"));
        validateItemForDisposal(buildingId, item);
        Optional<DisposalScan> existing = scanRepository.findBySession_IdAndEquipment_Id(session.getId(), item.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        DisposalScan scan = new DisposalScan();
        scan.setSession(session);
        scan.setEquipment(item);
        scan.setInventoryNumber(item.getInventoryNumber());
        scan.setEquipmentName(item.getName());
        scan.setTypeName(item.getType() == null ? "-" : item.getType().getName());
        scan.setLocationName(item.getLocation() == null ? "-" : item.getLocation().getName());
        DisposalScan saved = scanRepository.save(scan);
        session.incrementScans();
        sessionRepository.save(session);
        logService.log(actor, "В сессию утилизации № " + session.getId() + " добавлено " + item.getInventoryNumber());
        return saved;
    }

    @Transactional
    public DisposalSession finishActiveSession(Long buildingId, String sealNumber, String actor) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Выберите корпус перед завершением утилизации");
        }
        if (sealNumber == null || sealNumber.isBlank()) {
            throw new IllegalArgumentException("Введите или просканируйте номер пломбы");
        }
        DisposalSession session = findActive(buildingId)
                .orElseThrow(() -> new IllegalStateException("Нет активной сессии утилизации"));
        List<DisposalScan> scans = findScans(session.getId());
        if (scans.isEmpty()) {
            throw new IllegalStateException("Нельзя завершить пустую сессию утилизации");
        }
        Instant now = Instant.now();
        for (DisposalScan scan : scans) {
            EquipmentItem item = scan.getEquipment();
            item.setArchived(true);
            item.setArchivedAt(now);
            equipmentRepository.save(item);
        }
        session.setStatus(InventorySessionStatus.COMPLETED);
        session.setFinishedAt(now);
        session.setFinishedBy(actor);
        session.setSealNumber(sealNumber.trim());
        DisposalSession saved = sessionRepository.save(session);
        logService.log(actor, "Завершена сессия утилизации № " + saved.getId() + ", пломба " + saved.getSealNumber());
        return saved;
    }

    private void validateItemForDisposal(Long buildingId, EquipmentItem item) {
        if (item.isArchived()) {
            throw new IllegalArgumentException("Эта единица уже списана");
        }
        if (item.getLocation() == null || item.getLocation().getBuilding() == null
                || !buildingId.equals(item.getLocation().getBuilding().getId())) {
            throw new IllegalArgumentException("Оборудование числится в другом корпусе");
        }
        LocalDate purchaseDate = item.getPurchaseDate();
        if (purchaseDate == null || purchaseDate.isAfter(LocalDate.now().minusYears(EquipmentService.DEFAULT_USEFUL_LIFE_YEARS))) {
            throw new IllegalArgumentException("Эта единица еще не находится в списке на утилизацию");
        }
    }
}

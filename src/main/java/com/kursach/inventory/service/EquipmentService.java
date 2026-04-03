package com.kursach.inventory.service;

import com.kursach.inventory.domain.Employee;
import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.EquipmentType;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.repo.EmployeeRepository;
import com.kursach.inventory.repo.EquipmentItemRepository;
import com.kursach.inventory.repo.EquipmentTypeRepository;
import com.kursach.inventory.repo.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EquipmentService {
    private final EquipmentItemRepository repo;
    private final EquipmentTypeRepository typeRepository;
    private final LocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final ActionLogService logService;
    private final InventorySessionService inventorySessionService;

    public EquipmentService(EquipmentItemRepository repo,
                            EquipmentTypeRepository typeRepository,
                            LocationRepository locationRepository,
                            EmployeeRepository employeeRepository,
                            ActionLogService logService,
                            InventorySessionService inventorySessionService) {
        this.repo = repo;
        this.typeRepository = typeRepository;
        this.locationRepository = locationRepository;
        this.employeeRepository = employeeRepository;
        this.logService = logService;
        this.inventorySessionService = inventorySessionService;
    }

    public List<EquipmentItem> listActive() {
        return repo.findByArchivedFalseOrderByInventoryNumberAsc();
    }

    public List<EquipmentItem> listArchived() {
        return repo.findByArchivedTrueOrderByArchivedAtDesc();
    }

    public List<EquipmentItem> listWarehouse() {
        return repo.findByLocation_NameIgnoreCaseAndArchivedFalseOrderByInventoryNumberAsc("Склад");
    }

    public List<EquipmentItem> listByLocation(Long locationId) {
        if (locationId == null) {
            return List.of();
        }
        return repo.findByLocation_IdAndArchivedFalseOrderByInventoryNumberAsc(locationId);
    }

    public Optional<EquipmentItem> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public EquipmentItem saveOrUpdate(Long id,
                                      String inventoryNumber,
                                      String name,
                                      Long typeId,
                                      Long locationId,
                                      Long employeeId,
                                      BigDecimal purchasePrice,
                                      LocalDate purchaseDate,
                                      String actor) {
        EquipmentItem item = (id == null) ? new EquipmentItem() : repo.findById(id).orElseThrow();
        boolean creating = item.getId() == null;
        item.setInventoryNumber(inventoryNumber.trim());
        item.setName(name.trim());
        item.setType(resolveType(typeId));
        item.setLocation(resolveLocation(locationId));
        item.setAssignedTo(resolveEmployee(employeeId));
        item.setPurchasePrice(purchasePrice == null ? BigDecimal.ZERO : purchasePrice);
        item.setPurchaseDate(purchaseDate);

        EquipmentItem saved = repo.save(item);
        if (creating) {
            logService.log(actor, "Добавлено оборудование " + saved.getInventoryNumber());
        } else {
            logService.log(actor, "Обновлено оборудование " + saved.getInventoryNumber());
        }
        return saved;
    }

    @Transactional
    public EquipmentItem updatePlacement(Long id, Long locationId, Long employeeId, String actor) {
        EquipmentItem item = repo.findById(id).orElseThrow();
        if (locationId != null) {
            item.setLocation(resolveLocation(locationId));
        }
        item.setAssignedTo(resolveEmployee(employeeId));
        EquipmentItem saved = repo.save(item);
        logService.log(actor, "Перераспределено оборудование " + saved.getInventoryNumber());
        return saved;
    }

    @Transactional
    public EquipmentItem scanInventoryCode(String code, String actor) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Введите инвентарный номер");
        }
        EquipmentItem item = repo.findByInventoryNumber(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Оборудование с таким номером не найдено"));
        item.setLastInventoryScanAt(Instant.now());
        EquipmentItem saved = repo.save(item);
        inventorySessionService.registerScan(actor, saved);
        return saved;
    }

    @Transactional
    public void archive(Long id, String actor) {
        EquipmentItem item = repo.findById(id).orElseThrow();
        item.setArchived(true);
        item.setArchivedAt(Instant.now());
        repo.save(item);
        logService.log(actor, "Архивировано оборудование " + item.getInventoryNumber());
    }

    @Transactional
    public void unarchive(Long id, String actor) {
        EquipmentItem item = repo.findById(id).orElseThrow();
        item.setArchived(false);
        item.setArchivedAt(null);
        repo.save(item);
        logService.log(actor, "Возвращено из архива оборудование " + item.getInventoryNumber());
    }

    @Transactional
    public void delete(Long id, String actor) {
        EquipmentItem item = repo.findById(id).orElseThrow();
        repo.delete(item);
        logService.log(actor, "Удалено оборудование " + item.getInventoryNumber());
    }

    private EquipmentType resolveType(Long id) {
        if (id == null) throw new IllegalArgumentException("Не указан тип оборудования");
        return typeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Тип оборудования не найден"));
    }

    private Location resolveLocation(Long id) {
        if (id == null) throw new IllegalArgumentException("Не указано место установки");
        return locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Локация не найдена"));
    }

    private Employee resolveEmployee(Long id) {
        if (id == null) return null;
        return employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник не найден"));
    }
}

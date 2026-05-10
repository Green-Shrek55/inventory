package com.kursach.inventory.service;

import com.kursach.inventory.domain.Building;
import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.InventorySessionStatus;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.repo.BuildingRepository;
import com.kursach.inventory.repo.EquipmentItemRepository;
import com.kursach.inventory.repo.InventorySessionRepository;
import com.kursach.inventory.repo.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuildingService {
    private final BuildingRepository buildingRepository;
    private final LocationRepository locationRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final ActionLogService logService;

    public BuildingService(BuildingRepository buildingRepository,
                           LocationRepository locationRepository,
                           EquipmentItemRepository equipmentItemRepository,
                           InventorySessionRepository inventorySessionRepository,
                           ActionLogService logService) {
        this.buildingRepository = buildingRepository;
        this.locationRepository = locationRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.logService = logService;
    }

    public List<Building> listAll() {
        return buildingRepository.findAllByOrderByNameAsc();
    }

    public Building getById(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));
    }

    @Transactional
    public Building save(String name, String actor) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название корпуса не должно быть пустым");
        }
        String normalized = name.trim();
        Building building = buildingRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> buildingRepository.save(new Building(normalized)));
        logService.log(actor, "Сохранен корпус " + building.getName());
        return building;
    }

    @Transactional
    public Building rename(Long id, String name, String actor) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название корпуса не должно быть пустым");
        }
        Building building = getById(id);
        building.setName(name.trim());
        Building saved = buildingRepository.save(building);
        logService.log(actor, "Обновлен корпус " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCascade(Long id, String actor) {
        Building building = getById(id);
        List<Location> locations = locationRepository.findByBuilding_IdOrderByTypeAscNameAsc(id);
        for (Location location : locations) {
            if (equipmentItemRepository.existsByLocation_Id(location.getId())
                    || inventorySessionRepository.existsByLocation_IdAndStatus(location.getId(), InventorySessionStatus.ACTIVE)) {
                throw new IllegalArgumentException("Нельзя удалить корпус: в нем числится оборудование");
            }
        }
        for (Location location : locations) {
            detachFinishedInventorySessions(location.getId());
            locationRepository.delete(location);
        }
        buildingRepository.delete(building);
        logService.log(actor, "Удален корпус " + building.getName() + " вместе с локациями");
    }
    private void detachFinishedInventorySessions(Long locationId) {
        List<InventorySession> sessions = inventorySessionRepository.findByLocation_Id(locationId);
        for (InventorySession session : sessions) {
            if (session.getStatus() != InventorySessionStatus.ACTIVE) {
                session.setLocation(null);
            }
        }
        inventorySessionRepository.saveAll(sessions);
    }
}

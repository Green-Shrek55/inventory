package com.kursach.inventory.service;

import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.InventorySessionStatus;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.domain.LocationType;
import com.kursach.inventory.repo.BuildingRepository;
import com.kursach.inventory.repo.EquipmentItemRepository;
import com.kursach.inventory.repo.InventorySessionRepository;
import com.kursach.inventory.repo.LocationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LocationService {
    private static final Pattern CABINET_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final LocationRepository locationRepository;
    private final BuildingRepository buildingRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final ActionLogService logService;

    public LocationService(LocationRepository locationRepository,
                           BuildingRepository buildingRepository,
                           EquipmentItemRepository equipmentItemRepository,
                           InventorySessionRepository inventorySessionRepository,
                           ActionLogService logService) {
        this.locationRepository = locationRepository;
        this.buildingRepository = buildingRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.logService = logService;
    }

    public List<Location> listAll() {
        return locationRepository.findAll(Sort.by("building.name").ascending().and(Sort.by("type").ascending()).and(Sort.by("name").ascending()));
    }

    public List<Location> listByBuilding(Long buildingId) {
        if (buildingId == null) {
            return listAll();
        }
        return locationRepository.findByBuilding_IdOrderByTypeAscNameAsc(buildingId);
    }

    public List<Location> listWarehouses() {
        return locationRepository.findByTypeOrderByBuilding_NameAscNameAsc(LocationType.WAREHOUSE);
    }

    public List<Location> listWarehousesByBuilding(Long buildingId) {
        if (buildingId == null) {
            return listWarehouses();
        }
        return locationRepository.findByBuilding_IdOrderByTypeAscNameAsc(buildingId).stream()
                .filter(location -> location.getType() == LocationType.WAREHOUSE)
                .toList();
    }

    public List<Location> listCabinets() {
        return locationRepository.findByTypeOrderByBuilding_NameAscNameAsc(LocationType.CABINET);
    }

    public Location getById(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Локация не найдена"));
    }

    @Transactional
    public Location save(Long buildingId, String name, LocationType type, String actor) {
        Location location = new Location();
        location.setBuilding(buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Корпус не найден")));
        location.setName(normalizeName(name, type));
        location.setType(type);
        if (locationRepository.existsByBuilding_IdAndNameIgnoreCaseAndType(buildingId, location.getName(), type)) {
            throw new IllegalArgumentException("Такая локация уже существует в выбранном корпусе");
        }
        Location saved = locationRepository.save(location);
        logService.log(actor, "Сохранена локация " + saved.getDisplayName());
        return saved;
    }

    @Transactional
    public int createCabinetRange(Long buildingId, int from, int to, String actor) {
        if (from <= 0 || to <= 0 || from > to) {
            throw new IllegalArgumentException("Укажите корректный диапазон кабинетов");
        }
        buildingRepository.findById(buildingId).orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));
        int created = 0;
        for (int number = from; number <= to; number++) {
            String name = "Кабинет " + number;
            if (!locationRepository.existsByBuilding_IdAndNameIgnoreCaseAndType(buildingId, name, LocationType.CABINET)) {
                save(buildingId, name, LocationType.CABINET, actor);
                created++;
            }
        }
        logService.log(actor, "Массово создано кабинетов: " + created);
        return created;
    }

    @Transactional
    public int deleteEmptyCabinetRange(Long buildingId, int from, int to, String actor) {
        if (from <= 0 || to <= 0 || from > to) {
            throw new IllegalArgumentException("Укажите корректный диапазон кабинетов");
        }
        buildingRepository.findById(buildingId).orElseThrow(() -> new IllegalArgumentException("Корпус не найден"));
        int deleted = 0;
        List<Location> locations = locationRepository.findByBuilding_IdOrderByTypeAscNameAsc(buildingId);
        for (Location location : locations) {
            Integer cabinetNumber = cabinetNumber(location);
            if (cabinetNumber != null && cabinetNumber >= from && cabinetNumber <= to) {
                if (canDeleteLocation(location)) {
                    detachFinishedInventorySessions(location.getId());
                    locationRepository.delete(location);
                    deleted++;
                }
            }
        }
        logService.log(actor, "Массово удалено пустых кабинетов: " + deleted);
        return deleted;
    }

    @Transactional
    public Location update(Long id, String name, Long buildingId, LocationType type, String actor) {
        Location location = getById(id);
        location.setName(name);
        location.setBuilding(buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Корпус не найден")));
        location.setType(type);
        Location saved = locationRepository.save(location);
        logService.log(actor, "Обновлена локация " + saved.getDisplayName());
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        Location location = getById(id);
        if (!canDeleteLocation(location)) {
            throw new IllegalArgumentException("Нельзя удалить локацию: в ней числится оборудование");
        }
        detachFinishedInventorySessions(location.getId());
        locationRepository.delete(location);
        logService.log(actor, "Удалена локация " + location.getName());
    }

    private boolean canDeleteLocation(Location location) {
        Long locationId = location.getId();
        return !equipmentItemRepository.existsByLocation_Id(locationId)
                && !inventorySessionRepository.existsByLocation_IdAndStatus(locationId, InventorySessionStatus.ACTIVE);
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

    private String normalizeName(String name, LocationType type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название локации не должно быть пустым");
        }
        String trimmed = name.trim();
        if (type == LocationType.CABINET && trimmed.matches("\\d+")) {
            return "Кабинет " + trimmed;
        }
        return trimmed;
    }

    private Integer cabinetNumber(Location location) {
        if (location.getType() != LocationType.CABINET || location.getName() == null) {
            return null;
        }
        Matcher matcher = CABINET_NUMBER_PATTERN.matcher(location.getName());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package com.kursach.inventory.web.api;

import com.kursach.inventory.domain.Building;
import com.kursach.inventory.domain.DisposalScan;
import com.kursach.inventory.domain.DisposalSession;
import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.InventoryScan;
import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.domain.ReceiptRequest;
import com.kursach.inventory.service.BuildingService;
import com.kursach.inventory.service.DisposalSessionService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.InventorySessionService;
import com.kursach.inventory.service.LocationService;
import com.kursach.inventory.service.ReceiptRequestService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseApiController {

    private final BuildingService buildingService;
    private final LocationService locationService;
    private final EquipmentService equipmentService;
    private final InventorySessionService inventorySessionService;
    private final ReceiptRequestService receiptRequestService;
    private final DisposalSessionService disposalSessionService;

    public WarehouseApiController(BuildingService buildingService,
                                  LocationService locationService,
                                  EquipmentService equipmentService,
                                  InventorySessionService inventorySessionService,
                                  ReceiptRequestService receiptRequestService,
                                  DisposalSessionService disposalSessionService) {
        this.buildingService = buildingService;
        this.locationService = locationService;
        this.equipmentService = equipmentService;
        this.inventorySessionService = inventorySessionService;
        this.receiptRequestService = receiptRequestService;
        this.disposalSessionService = disposalSessionService;
    }

    @GetMapping("/me")
    public ApiMessage me(Authentication authentication) {
        return new ApiMessage("ok", actor(authentication));
    }

    @GetMapping("/buildings")
    public List<BuildingDto> buildings() {
        return buildingService.listAll().stream().map(this::buildingDto).toList();
    }

    @GetMapping("/locations")
    public List<LocationDto> locations(@RequestParam Long buildingId) {
        return locationService.listByBuilding(buildingId).stream().map(this::locationDto).toList();
    }

    @GetMapping("/equipment")
    public List<EquipmentDto> equipment(@RequestParam Long buildingId) {
        return equipmentService.listActive(buildingId).stream().map(this::equipmentDto).toList();
    }

    @GetMapping("/disposal-due")
    public List<EquipmentDto> disposalDue(@RequestParam Long buildingId) {
        return equipmentService.listDisposalDue(buildingId).stream().map(this::equipmentDto).toList();
    }

    @GetMapping("/receipt-requests")
    public List<ReceiptRequestDto> receiptRequests(@RequestParam Long buildingId) {
        return receiptRequestService.listByBuilding(buildingId).stream().map(this::receiptRequestDto).toList();
    }

    @PostMapping("/receipt/scan")
    public ApiMessage receiptScan(@RequestBody ReceiptScanRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        Long requestId = requireId(request.requestId(), "Укажите заявку");
        String code = requireCode(request.code());
        ReceiptRequest receipt = receiptRequestService.getById(requestId);
        if (receipt.getBuilding() == null || !buildingId.equals(receipt.getBuilding().getId())) {
            throw new IllegalArgumentException("Заявка относится к другому корпусу");
        }
        Long warehouseId = locationService.listWarehousesByBuilding(buildingId).stream()
                .map(Location::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Для выбранного корпуса не создан склад"));
        receiptRequestService.acceptCode(requestId, code, warehouseId, actor(authentication));
        return new ApiMessage("ok", "Единица принята на склад");
    }

    @PostMapping("/receipt/start")
    public ApiMessage receiptStart(@RequestBody ReceiptSessionRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        Long requestId = requireId(request.requestId(), "Укажите заявку");
        ReceiptRequest receipt = receiptRequestService.getById(requestId);
        if (receipt.getBuilding() == null || !buildingId.equals(receipt.getBuilding().getId())) {
            throw new IllegalArgumentException("Заявка относится к другому корпусу");
        }
        receiptRequestService.startAcceptance(requestId, actor(authentication));
        return new ApiMessage("ok", "Приемка начата");
    }

    @PostMapping("/receipt/finish")
    public ApiMessage receiptFinish(@RequestBody ReceiptSessionRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        Long requestId = requireId(request.requestId(), "Укажите заявку");
        ReceiptRequest receipt = receiptRequestService.getById(requestId);
        if (receipt.getBuilding() == null || !buildingId.equals(receipt.getBuilding().getId())) {
            throw new IllegalArgumentException("Заявка относится к другому корпусу");
        }
        receiptRequestService.finishAcceptance(requestId, actor(authentication));
        return new ApiMessage("ok", "Приемка завершена");
    }

    @PostMapping("/placement")
    public ApiMessage placement(@RequestBody PlacementRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        Long locationId = requireId(request.locationId(), "Укажите кабинет или склад");
        String code = requireCode(request.code());
        EquipmentItem item = equipmentService.findByInventoryNumber(code)
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено"));
        if (!belongsToBuilding(item, buildingId)) {
            throw new IllegalArgumentException("Оборудование числится в другом корпусе");
        }
        Location target = locationService.getById(locationId);
        if (target.getBuilding() == null || !buildingId.equals(target.getBuilding().getId())) {
            throw new IllegalArgumentException("Выбранное место относится к другому корпусу");
        }
        equipmentService.updatePlacement(item.getId(), locationId, null, actor(authentication));
        return new ApiMessage("ok", "Размещение обновлено");
    }

    @GetMapping("/inventory/state")
    public InventoryStateDto inventoryState(@RequestParam Long buildingId) {
        InventorySession active = inventorySessionService.findActive().orElse(null);
        if (active == null || active.getLocation() == null
                || active.getLocation().getBuilding() == null
                || !buildingId.equals(active.getLocation().getBuilding().getId())) {
            return new InventoryStateDto(null, null, List.of(), List.of(), List.of());
        }
        Long locationId = active.getLocation().getId();
        List<EquipmentItem> expected = equipmentService.listByLocation(locationId);
        List<InventoryScan> scans = inventorySessionService.findScans(active.getId());
        Set<Long> matchedIds = scans.stream()
                .map(InventoryScan::getEquipment)
                .filter(item -> item.getLocation() != null
                        && item.getLocation().getId() != null
                        && item.getLocation().getId().equals(locationId))
                .map(EquipmentItem::getId)
                .collect(Collectors.toSet());
        List<InventoryScanDto> matched = scans.stream()
                .filter(scan -> scan.getEquipment().getLocation() != null
                        && scan.getEquipment().getLocation().getId() != null
                        && scan.getEquipment().getLocation().getId().equals(locationId))
                .map(this::inventoryScanDto)
                .toList();
        List<EquipmentDto> missing = expected.stream()
                .filter(item -> !matchedIds.contains(item.getId()))
                .map(this::equipmentDto)
                .toList();
        List<InventoryScanDto> surplus = scans.stream()
                .filter(scan -> scan.getEquipment().getLocation() == null
                        || scan.getEquipment().getLocation().getId() == null
                        || !scan.getEquipment().getLocation().getId().equals(locationId))
                .map(this::inventoryScanDto)
                .toList();
        return new InventoryStateDto(inventorySessionDto(active), locationDto(active.getLocation()), matched, missing, surplus);
    }

    @PostMapping("/inventory/start")
    public ApiMessage inventoryStart(@RequestBody InventoryStartRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        Long locationId = requireId(request.locationId(), "Укажите кабинет или склад");
        Location location = locationService.getById(locationId);
        if (location.getBuilding() == null || !buildingId.equals(location.getBuilding().getId())) {
            throw new IllegalArgumentException("Выбранное место относится к другому корпусу");
        }
        InventorySession session = inventorySessionService.startSession(actor(authentication), locationId);
        return new ApiMessage("ok", "Сессия #" + session.getId() + " начата");
    }

    @PostMapping("/inventory/scan")
    public ApiMessage inventoryScan(@RequestBody InventoryScanRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        InventorySession active = inventorySessionService.findActive()
                .orElseThrow(() -> new IllegalStateException("Сначала начните сессию инвентаризации"));
        if (active.getLocation() == null || active.getLocation().getBuilding() == null
                || !buildingId.equals(active.getLocation().getBuilding().getId())) {
            throw new IllegalStateException("Активная сессия относится к другому корпусу");
        }
        equipmentService.scanInventoryCode(requireCode(request.code()), actor(authentication));
        return new ApiMessage("ok", "Код отсканирован");
    }

    @PostMapping("/inventory/finish")
    public ApiMessage inventoryFinish(Authentication authentication) {
        InventorySession session = inventorySessionService.finishActiveSession(actor(authentication));
        return new ApiMessage("ok", "Сессия #" + session.getId() + " завершена");
    }

    @PostMapping("/disposal")
    public ApiMessage disposal(@RequestBody DisposalRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        EquipmentItem item = equipmentService.findByInventoryNumber(requireCode(request.code()))
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено"));
        if (!belongsToBuilding(item, buildingId)) {
            throw new IllegalArgumentException("Оборудование числится в другом корпусе");
        }
        boolean due = equipmentService.listDisposalDue(buildingId).stream()
                .anyMatch(dueItem -> dueItem.getId().equals(item.getId()));
        if (!due) {
            throw new IllegalArgumentException("Эта единица не находится в списке на утилизацию");
        }
        equipmentService.archive(item.getId(), actor(authentication));
        return new ApiMessage("ok", "Единица перенесена в архив");
    }

    @GetMapping("/disposal/state")
    public DisposalStateDto disposalState(@RequestParam Long buildingId) {
        DisposalSession active = disposalSessionService.findActive(buildingId).orElse(null);
        List<DisposalScanDto> scans = active == null
                ? List.of()
                : disposalSessionService.findScans(active.getId()).stream().map(this::disposalScanDto).toList();
        return new DisposalStateDto(active == null ? null : disposalSessionDto(active), scans);
    }

    @PostMapping("/disposal/start")
    public ApiMessage disposalStart(@RequestBody DisposalSessionRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        DisposalSession session = disposalSessionService.startSession(buildingId, actor(authentication));
        return new ApiMessage("ok", "Сессия утилизации #" + session.getId() + " начата");
    }

    @PostMapping("/disposal/scan")
    public ApiMessage disposalScan(@RequestBody DisposalRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        disposalSessionService.scan(buildingId, requireCode(request.code()), actor(authentication));
        return new ApiMessage("ok", "Единица добавлена в сессию утилизации");
    }

    @PostMapping("/disposal/finish")
    public ApiMessage disposalFinish(@RequestBody DisposalFinishRequest request, Authentication authentication) {
        Long buildingId = requireId(request.buildingId(), "Укажите корпус");
        DisposalSession session = disposalSessionService.finishActiveSession(buildingId, request.sealNumber(), actor(authentication));
        return new ApiMessage("ok", "Сессия утилизации #" + session.getId() + " завершена");
    }

    private boolean belongsToBuilding(EquipmentItem item, Long buildingId) {
        return item.getLocation() != null
                && item.getLocation().getBuilding() != null
                && buildingId.equals(item.getLocation().getBuilding().getId());
    }

    private Long requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    private String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Введите или отсканируйте код");
        }
        return code.trim();
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "mobile" : authentication.getName();
    }

    private BuildingDto buildingDto(Building building) {
        return new BuildingDto(building.getId(), building.getName());
    }

    private LocationDto locationDto(Location location) {
        return new LocationDto(
                location.getId(),
                location.getName(),
                location.getType().name(),
                location.getBuilding() != null ? location.getBuilding().getId() : null,
                location.getBuilding() != null ? location.getBuilding().getName() : null
        );
    }

    private EquipmentDto equipmentDto(EquipmentItem item) {
        return new EquipmentDto(
                item.getId(),
                item.getInventoryNumber(),
                item.getName(),
                item.getType() != null ? item.getType().getName() : "-",
                item.getLocation() != null ? item.getLocation().getId() : null,
                item.getLocation() != null ? item.getLocation().getName() : "-",
                item.getLocation() != null && item.getLocation().getBuilding() != null ? item.getLocation().getBuilding().getName() : "-",
                item.getAssignedTo() != null ? item.getAssignedTo().getFullName() : "-"
        );
    }

    private ReceiptRequestDto receiptRequestDto(ReceiptRequest request) {
        long accepted = request.getItems().stream().filter(item -> item.isAccepted()).count();
        return new ReceiptRequestDto(
                request.getId(),
                request.getTitle(),
                request.getSupplier(),
                request.getStatus().name(),
                request.getItems().size(),
                accepted,
                request.getItems().stream()
                        .map(item -> new ReceiptItemDto(item.getId(), item.getExpectedInventoryNumber(), item.getName(), item.isAccepted()))
                        .toList()
        );
    }

    private InventorySessionDto inventorySessionDto(InventorySession session) {
        return new InventorySessionDto(session.getId(), session.getScannedCount());
    }

    private InventoryScanDto inventoryScanDto(InventoryScan scan) {
        return new InventoryScanDto(
                scan.getInventoryNumber(),
                scan.getEquipmentName(),
                scan.getTypeName(),
                scan.getLocationName(),
                scan.getAssignedPerson()
        );
    }

    private DisposalSessionDto disposalSessionDto(DisposalSession session) {
        return new DisposalSessionDto(session.getId(), session.getScannedCount());
    }

    private DisposalScanDto disposalScanDto(DisposalScan scan) {
        return new DisposalScanDto(
                scan.getInventoryNumber(),
                scan.getEquipmentName(),
                scan.getTypeName(),
                scan.getLocationName()
        );
    }

    public record ApiMessage(String status, String message) {}
    public record BuildingDto(Long id, String name) {}
    public record LocationDto(Long id, String name, String type, Long buildingId, String buildingName) {}
    public record EquipmentDto(Long id, String inventoryNumber, String name, String type, Long locationId,
                               String locationName, String buildingName, String assignedTo) {}
    public record ReceiptRequestDto(Long id, String title, String supplier, String status, int total, long accepted,
                                    List<ReceiptItemDto> items) {}
    public record ReceiptItemDto(Long id, String code, String name, boolean accepted) {}
    public record InventorySessionDto(Long id, int scannedCount) {}
    public record InventoryScanDto(String inventoryNumber, String name, String type, String locationName, String assignedTo) {}
    public record InventoryStateDto(InventorySessionDto session, LocationDto location, List<InventoryScanDto> matched,
                                    List<EquipmentDto> missing, List<InventoryScanDto> surplus) {}
    public record DisposalSessionDto(Long id, int scannedCount) {}
    public record DisposalScanDto(String inventoryNumber, String name, String type, String locationName) {}
    public record DisposalStateDto(DisposalSessionDto session, List<DisposalScanDto> scans) {}
    public record ReceiptScanRequest(Long buildingId, Long requestId, String code) {}
    public record ReceiptSessionRequest(Long buildingId, Long requestId) {}
    public record PlacementRequest(Long buildingId, Long locationId, String code) {}
    public record InventoryStartRequest(Long buildingId, Long locationId) {}
    public record InventoryScanRequest(Long buildingId, String code) {}
    public record DisposalRequest(Long buildingId, String code) {}
    public record DisposalSessionRequest(Long buildingId) {}
    public record DisposalFinishRequest(Long buildingId, String sealNumber) {}
}

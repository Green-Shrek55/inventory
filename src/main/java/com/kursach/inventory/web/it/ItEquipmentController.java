package com.kursach.inventory.web.it;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.domain.LocationType;
import com.kursach.inventory.service.DisposalActDocumentService;
import com.kursach.inventory.service.DisposalSessionService;
import com.kursach.inventory.service.EmployeeService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.EquipmentTypeService;
import com.kursach.inventory.service.LocationService;
import com.kursach.inventory.service.ReceiptRequestService;
import com.kursach.inventory.service.BuildingService;
import com.kursach.inventory.web.dto.EquipmentItemForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.kursach.inventory.web.it.WarehouseContextController.BUILDING_ID_SESSION_KEY;
import static com.kursach.inventory.web.it.WarehouseContextController.BULK_PLACEMENT_ENABLED_SESSION_KEY;
import static com.kursach.inventory.web.it.WarehouseContextController.PLACEMENT_LOCATION_ID_SESSION_KEY;
@Controller
@RequestMapping("/warehouse/equipment")
public class ItEquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentTypeService typeService;
    private final LocationService locationService;
    private final EmployeeService employeeService;
    private final ReceiptRequestService receiptRequestService;
    private final BuildingService buildingService;
    private final DisposalSessionService disposalSessionService;
    private final DisposalActDocumentService disposalActDocumentService;

    public ItEquipmentController(EquipmentService equipmentService,
                                 EquipmentTypeService typeService,
                                 LocationService locationService,
                                 EmployeeService employeeService,
                                 ReceiptRequestService receiptRequestService,
                                 BuildingService buildingService,
                                 DisposalSessionService disposalSessionService,
                                 DisposalActDocumentService disposalActDocumentService) {
        this.equipmentService = equipmentService;
        this.typeService = typeService;
        this.locationService = locationService;
        this.employeeService = employeeService;
        this.receiptRequestService = receiptRequestService;
        this.buildingService = buildingService;
        this.disposalSessionService = disposalSessionService;
        this.disposalActDocumentService = disposalActDocumentService;
    }

    @ModelAttribute("warehouseLocationId")
    public Long warehouseLocationId() {
        return locationService.listWarehouses().stream()
                .filter(loc -> loc.getType() == LocationType.WAREHOUSE)
                .map(Location::getId)
                .findFirst()
                .orElse(null);
    }

    @GetMapping
    public String list(@RequestParam(value = "warehouseId", required = false) Long warehouseId,
                       @RequestParam(value = "placementCode", required = false) String placementCode,
                       HttpSession session,
                       Model model) {
        Long buildingId = currentBuildingId(session);
        model.addAttribute("activeEquipment", equipmentService.listActive(buildingId));
        model.addAttribute("warehouseEquipment", warehouseId == null ? equipmentService.listWarehouseByBuilding(buildingId) : equipmentService.listWarehouse(warehouseId));
        model.addAttribute("archivedEquipment", equipmentService.listArchived(buildingId));
        model.addAttribute("locations", locationService.listByBuilding(buildingId));
        model.addAttribute("warehouseLocations", locationService.listWarehousesByBuilding(buildingId));
        model.addAttribute("buildings", buildingService.listAll());
        model.addAttribute("selectedBuildingId", buildingId);
        Long fixedPlacementLocationId = currentPlacementLocationId(session);
        boolean bulkPlacementEnabled = currentBulkPlacementEnabled(session);
        model.addAttribute("fixedPlacementLocationId", fixedPlacementLocationId);
        model.addAttribute("fixedPlacementLocation", fixedPlacementLocationId == null ? null : locationService.getById(fixedPlacementLocationId));
        model.addAttribute("bulkPlacementEnabled", bulkPlacementEnabled);
        model.addAttribute("disposalDueEquipment", equipmentService.listDisposalDue(buildingId));
        var activeDisposalSession = disposalSessionService.findActive(buildingId).orElse(null);
        model.addAttribute("activeDisposalSession", activeDisposalSession);
        model.addAttribute("activeDisposalScans", activeDisposalSession == null
                ? java.util.List.of()
                : disposalSessionService.findScans(activeDisposalSession.getId()));
        model.addAttribute("disposalSessions", disposalSessionService.recentSessions());
        model.addAttribute("receiptRequests", receiptRequestService.listByBuilding(buildingId));
        model.addAttribute("selectedWarehouseId", warehouseId);
        model.addAttribute("placementCode", placementCode);
        if (placementCode != null && !placementCode.isBlank()) {
            EquipmentItem placementItem = equipmentService.findByInventoryNumber(placementCode).orElse(null);
            boolean itemInSelectedBuilding = placementItem != null
                    && (buildingId == null
                    || (placementItem.getLocation() != null
                    && placementItem.getLocation().getBuilding() != null
                    && buildingId.equals(placementItem.getLocation().getBuilding().getId())));
            model.addAttribute("placementItem", itemInSelectedBuilding ? placementItem : null);
            model.addAttribute("placementOutOfBuilding", placementItem != null && !itemInSelectedBuilding);
            if (itemInSelectedBuilding && bulkPlacementEnabled && fixedPlacementLocationId != null) {
                try {
                    Location targetLocation = locationService.getById(fixedPlacementLocationId);
                    boolean targetInSelectedBuilding = buildingId == null
                            || (targetLocation.getBuilding() != null && buildingId.equals(targetLocation.getBuilding().getId()));
                    if (targetInSelectedBuilding) {
                        equipmentService.updatePlacement(placementItem.getId(), fixedPlacementLocationId, null, "auto-placement");
                        model.addAttribute("placementAutoApplied", true);
                        model.addAttribute("placementItem", equipmentService.findByInventoryNumber(placementCode).orElse(placementItem));
                    }
                } catch (IllegalArgumentException ignored) {
                    session.removeAttribute(PLACEMENT_LOCATION_ID_SESSION_KEY);
                }
            }
        }
        model.addAttribute("employees", employeeService.listAll());
        return "it/equipment/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("form")) {
            EquipmentItemForm form = new EquipmentItemForm();
            form.setPurchasePrice(BigDecimal.ZERO);
            form.setPurchaseDate(LocalDate.now());
            model.addAttribute("form", form);
        }
        populateReferenceData(model);
        model.addAttribute("editMode", false);
        return "it/equipment/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") EquipmentItemForm form,
                         BindingResult bindingResult,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateReferenceData(model);
            model.addAttribute("editMode", false);
            return "it/equipment/form";
        }
        try {
            equipmentService.saveOrUpdate(null, form.getInventoryNumber(), form.getName(),
                    form.getTypeId(), form.getLocationId(), form.getEmployeeId(),
                    form.getPurchasePrice(), form.getPurchaseDate(), actor(auth));
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            bindingResult.reject("error", ex.getMessage());
            populateReferenceData(model);
            model.addAttribute("editMode", false);
            return "it/equipment/form";
        }
        redirectAttributes.addFlashAttribute("message", "Оборудование добавлено");
        return "redirect:/warehouse/equipment";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        EquipmentItem item = equipmentService.findById(id).orElseThrow();
        if (!model.containsAttribute("form")) {
            EquipmentItemForm form = new EquipmentItemForm();
            form.setId(item.getId());
            form.setInventoryNumber(item.getInventoryNumber());
            form.setName(item.getName());
            form.setTypeId(item.getType() != null ? item.getType().getId() : null);
            form.setLocationId(item.getLocation() != null ? item.getLocation().getId() : null);
            form.setEmployeeId(item.getAssignedTo() != null ? item.getAssignedTo().getId() : null);
            form.setPurchasePrice(item.getPurchasePrice());
            form.setPurchaseDate(item.getPurchaseDate());
            model.addAttribute("form", form);
        }
        populateReferenceData(model);
        model.addAttribute("item", item);
        model.addAttribute("editMode", true);
        return "it/equipment/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") EquipmentItemForm form,
                         BindingResult bindingResult,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateReferenceData(model);
            model.addAttribute("editMode", true);
            model.addAttribute("item", equipmentService.findById(id).orElseThrow());
            return "it/equipment/form";
        }
        try {
            equipmentService.saveOrUpdate(id, form.getInventoryNumber(), form.getName(),
                    form.getTypeId(), form.getLocationId(), form.getEmployeeId(),
                    form.getPurchasePrice(), form.getPurchaseDate(), actor(auth));
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            bindingResult.reject("error", ex.getMessage());
            populateReferenceData(model);
            model.addAttribute("editMode", true);
            model.addAttribute("item", equipmentService.findById(id).orElseThrow());
            return "it/equipment/form";
        }
        redirectAttributes.addFlashAttribute("message", "Запись обновлена");
        return "redirect:/warehouse/equipment";
    }

    @PostMapping("/{id}/placement")
    public String updatePlacement(@PathVariable Long id,
                                  @RequestParam Long locationId,
                                  @RequestParam(required = false) Long employeeId,
                                  HttpSession session,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {
        try {
            equipmentService.updatePlacement(id, locationId, employeeId, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Перемещение выполнено");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment";
    }

    @PostMapping("/placement")
    public String updatePlacementByCode(@RequestParam String inventoryNumber,
                                        @RequestParam Long locationId,
                                        HttpSession session,
                                        Authentication auth,
                                        RedirectAttributes redirectAttributes) {
        Long buildingId = currentBuildingId(session);
        try {
            EquipmentItem item = equipmentService.findByInventoryNumber(inventoryNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Оборудование с таким номером не найдено"));
            Location targetLocation = locationService.getById(locationId);
            if (buildingId != null && (targetLocation.getBuilding() == null || !buildingId.equals(targetLocation.getBuilding().getId()))) {
                throw new IllegalArgumentException("Выбранное место не относится к текущему корпусу");
            }
            equipmentService.updatePlacement(item.getId(), locationId, null, actor(auth));
            redirectAttributes.addAttribute("placementCode", item.getInventoryNumber());
            redirectAttributes.addFlashAttribute("message", "Размещение обновлено для " + item.getInventoryNumber());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addAttribute("placementCode", inventoryNumber);
        }
        return "redirect:/warehouse/equipment#placement";
    }

    @PostMapping("/receipt-requests/{requestId}/scan")
    public String acceptReceiptItem(@PathVariable Long requestId,
                                    @RequestParam String code,
                                    HttpSession session,
                                    Authentication auth,
                                    RedirectAttributes redirectAttributes) {
        try {
            Long buildingId = currentBuildingId(session);
            if (buildingId == null) {
                throw new IllegalArgumentException("Выберите корпус справа сверху перед приемкой");
            }
            var request = receiptRequestService.getById(requestId);
            if (request.getBuilding() == null || !buildingId.equals(request.getBuilding().getId())) {
                throw new IllegalArgumentException("Эта заявка относится к другому корпусу");
            }
            Long warehouseId = locationService.listWarehousesByBuilding(buildingId).stream()
                    .map(Location::getId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Для текущего корпуса не создан склад"));
            receiptRequestService.acceptCode(requestId, code, warehouseId, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Единица принята на склад");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#acceptance";
    }

    @PostMapping("/receipt-requests/{requestId}/start")
    public String startReceiptAcceptance(@PathVariable Long requestId,
                                         HttpSession session,
                                         Authentication auth,
                                         RedirectAttributes redirectAttributes) {
        try {
            Long buildingId = currentBuildingId(session);
            if (buildingId == null) {
                throw new IllegalArgumentException("Выберите корпус справа сверху перед приемкой");
            }
            var request = receiptRequestService.getById(requestId);
            if (request.getBuilding() == null || !buildingId.equals(request.getBuilding().getId())) {
                throw new IllegalArgumentException("Эта заявка относится к другому корпусу");
            }
            receiptRequestService.startAcceptance(requestId, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Приемка начата");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#acceptance";
    }

    @PostMapping("/receipt-requests/{requestId}/finish")
    public String finishReceiptAcceptance(@PathVariable Long requestId,
                                          HttpSession session,
                                          Authentication auth,
                                          RedirectAttributes redirectAttributes) {
        try {
            Long buildingId = currentBuildingId(session);
            if (buildingId == null) {
                throw new IllegalArgumentException("Выберите корпус справа сверху перед завершением приемки");
            }
            var request = receiptRequestService.getById(requestId);
            if (request.getBuilding() == null || !buildingId.equals(request.getBuilding().getId())) {
                throw new IllegalArgumentException("Эта заявка относится к другому корпусу");
            }
            receiptRequestService.finishAcceptance(requestId, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Приемка завершена. Акт DOCX доступен в строке заявки.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#acceptance";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          Authentication auth,
                          RedirectAttributes redirectAttributes) {
        equipmentService.archive(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Перемещено в архив");
        return "redirect:/warehouse/equipment";
    }

    @PostMapping("/disposal/start")
    public String startDisposalSession(HttpSession session,
                                       Authentication auth,
                                       RedirectAttributes redirectAttributes) {
        try {
            disposalSessionService.startSession(currentBuildingId(session), actor(auth));
            redirectAttributes.addFlashAttribute("message", "Сессия утилизации начата");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#disposal";
    }

    @PostMapping("/disposal/scan")
    public String scanDisposalItem(@RequestParam String code,
                                   HttpSession session,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        try {
            disposalSessionService.scan(currentBuildingId(session), code, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Единица добавлена в сессию утилизации");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#disposal";
    }

    @PostMapping("/disposal/finish")
    public String finishDisposalSession(@RequestParam String sealNumber,
                                        HttpSession session,
                                        Authentication auth,
                                        RedirectAttributes redirectAttributes) {
        try {
            var finished = disposalSessionService.finishActiveSession(currentBuildingId(session), sealNumber, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Сессия утилизации завершена. Акт DOCX доступен в истории.");
            redirectAttributes.addFlashAttribute("finishedDisposalSessionId", finished.getId());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/equipment#disposal";
    }

    @GetMapping("/disposal/export/{sessionId}")
    public ResponseEntity<byte[]> exportDisposalAct(@PathVariable Long sessionId) {
        byte[] document = disposalActDocumentService.buildAct(sessionId);
        String filename = disposalActDocumentService.filename(sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(document);
    }

    @PostMapping("/{id}/unarchive")
    public String unarchive(@PathVariable Long id,
                            Authentication auth,
                            RedirectAttributes redirectAttributes) {
        equipmentService.unarchive(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Возвращено в актив");
        return "redirect:/warehouse/equipment";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication auth,
                         RedirectAttributes redirectAttributes) {
        equipmentService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Оборудование удалено");
        return "redirect:/warehouse/equipment";
    }

    private void populateReferenceData(Model model) {
        model.addAttribute("types", typeService.listAll());
        model.addAttribute("locations", locationService.listAll());
        model.addAttribute("employees", employeeService.listAll());
    }

    private String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }

    private Long currentBuildingId(HttpSession session) {
        Object value = session.getAttribute(BUILDING_ID_SESSION_KEY);
        return value instanceof Long id ? id : null;
    }

    private Long currentPlacementLocationId(HttpSession session) {
        Object value = session.getAttribute(PLACEMENT_LOCATION_ID_SESSION_KEY);
        return value instanceof Long id ? id : null;
    }

    private boolean currentBulkPlacementEnabled(HttpSession session) {
        Object value = session.getAttribute(BULK_PLACEMENT_ENABLED_SESSION_KEY);
        return value instanceof Boolean enabled && enabled;
    }
}

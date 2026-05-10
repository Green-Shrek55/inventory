package com.kursach.inventory.web.it;

import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.EquipmentTypeService;
import com.kursach.inventory.service.LocationService;
import com.kursach.inventory.service.BuildingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.kursach.inventory.web.it.WarehouseContextController.BUILDING_ID_SESSION_KEY;

@Controller
@RequestMapping("/warehouse")
public class ItDashboardController {
    private final EquipmentService equipmentService;
    private final EquipmentTypeService typeService;
    private final LocationService locationService;
    private final BuildingService buildingService;

    public ItDashboardController(EquipmentService equipmentService,
                                 EquipmentTypeService typeService,
                                 LocationService locationService,
                                 BuildingService buildingService) {
        this.equipmentService = equipmentService;
        this.typeService = typeService;
        this.locationService = locationService;
        this.buildingService = buildingService;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "typeIds", required = false) List<Long> typeIds,
                            @RequestParam(value = "locationId", required = false) Long locationId,
                            @RequestParam(value = "inventoryNumber", required = false) String inventoryNumber,
                            HttpSession session,
                            Authentication authentication,
                            Model model) {
        Long buildingId = currentBuildingId(session);
        List<com.kursach.inventory.domain.EquipmentItem> activeEquipment = equipmentService.listActive(buildingId);
        List<com.kursach.inventory.domain.EquipmentItem> filteredEquipment = activeEquipment.stream()
                .filter(item -> typeIds == null || typeIds.isEmpty()
                        || (item.getType() != null && typeIds.contains(item.getType().getId())))
                .filter(item -> locationId == null
                        || (item.getLocation() != null && locationId.equals(item.getLocation().getId())))
                .filter(item -> inventoryNumber == null || inventoryNumber.isBlank()
                        || item.getInventoryNumber().toLowerCase().contains(inventoryNumber.trim().toLowerCase()))
                .toList();

        model.addAttribute("activeEquipment", activeEquipment);
        model.addAttribute("filteredEquipment", filteredEquipment);
        model.addAttribute("archivedEquipment", equipmentService.listArchived(buildingId));
        model.addAttribute("warehouseEquipment", equipmentService.listWarehouseByBuilding(buildingId));
        model.addAttribute("disposalDueEquipment", equipmentService.listDisposalDue(buildingId));
        model.addAttribute("equipmentTypes", typeService.listAll());
        model.addAttribute("locations", locationService.listByBuilding(buildingId));
        model.addAttribute("buildings", buildingService.listAll());
        model.addAttribute("selectedBuildingId", buildingId);
        model.addAttribute("selectedTypeIds", typeIds == null ? List.of() : typeIds);
        model.addAttribute("selectedLocationId", locationId);
        model.addAttribute("selectedInventoryNumber", inventoryNumber == null ? "" : inventoryNumber.trim());
        model.addAttribute("isAdmin", hasRole(authentication, "ROLE_ADMIN"));
        return "it/dashboard";
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private Long currentBuildingId(HttpSession session) {
        Object value = session.getAttribute(BUILDING_ID_SESSION_KEY);
        return value instanceof Long id ? id : null;
    }

}

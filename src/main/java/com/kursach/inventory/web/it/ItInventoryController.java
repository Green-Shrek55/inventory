package com.kursach.inventory.web.it;

import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.InventorySessionService;
import com.kursach.inventory.service.LocationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/it/inventory")
public class ItInventoryController {

    private final EquipmentService equipmentService;
    private final InventorySessionService sessionService;
    private final LocationService locationService;

    public ItInventoryController(EquipmentService equipmentService,
                                 InventorySessionService sessionService,
                                 LocationService locationService) {
        this.equipmentService = equipmentService;
        this.sessionService = sessionService;
        this.locationService = locationService;
    }

    @GetMapping
    public String page(@RequestParam(value = "locationId", required = false) Long locationId,
                       Model model) {
        InventorySession activeSession = sessionService.findActive().orElse(null);
        List<Location> locations = locationService.listAll();
        model.addAttribute("locations", locations);
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("recentSessions", sessionService.recentSessions());

        Long selectedLocationId = null;
        String currentLocationName = null;
        if (activeSession != null && activeSession.getLocation() != null) {
            selectedLocationId = activeSession.getLocation().getId();
            currentLocationName = activeSession.getLocation().getName();
        } else if (locationId != null) {
            selectedLocationId = locationId;
            currentLocationName = locations.stream()
                    .filter(loc -> loc.getId().equals(locationId))
                    .map(Location::getName)
                    .findFirst()
                    .orElse(null);
        }

        model.addAttribute("selectedLocationId", selectedLocationId);
        model.addAttribute("currentLocationName", currentLocationName);
        model.addAttribute("locationItems", equipmentService.listByLocation(selectedLocationId));
        return "it/inventory";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam("code") String code,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            equipmentService.scanInventoryCode(code, actor(authentication));
            redirectAttributes.addFlashAttribute("message", "Единица " + code + " успешно отсканирована");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/it/inventory";
    }

    @PostMapping("/start")
    public String startSession(@RequestParam(value = "locationId", required = false) Long locationId,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (locationId == null) {
            redirectAttributes.addFlashAttribute("error", "Выберите локацию для инвентаризации");
            return "redirect:/it/inventory";
        }
        try {
            InventorySession session = sessionService.startSession(actor(authentication), locationId);
            redirectAttributes.addFlashAttribute("message", "Сессия #" + session.getId() + " начата");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            if (locationId != null) {
                redirectAttributes.addAttribute("locationId", locationId);
            }
            return "redirect:/it/inventory";
        }
        redirectAttributes.addAttribute("locationId", locationId);
        return "redirect:/it/inventory";
    }

    @PostMapping("/finish")
    public String finishSession(Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            InventorySession session = sessionService.finishActiveSession(actor(authentication));
            redirectAttributes.addFlashAttribute("message", "Сессия #" + session.getId() + " завершена");
            if (session.getLocation() != null) {
                redirectAttributes.addAttribute("locationId", session.getLocation().getId());
            }
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/it/inventory";
    }

    @GetMapping("/export/{sessionId}")
    public ResponseEntity<byte[]> export(@PathVariable Long sessionId) {
        InventorySession session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));
        List<com.kursach.inventory.domain.InventoryScan> scans = sessionService.findScans(sessionId);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Сессия;Начало;Завершено;Сканов;Дата сканирования;Инвентарный номер;Название;Тип;Локация;Ответственный\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        String started = session.getStartedAt() != null ? dtf.format(session.getStartedAt().atZone(java.time.ZoneId.systemDefault())) : "";
        String finished = session.getFinishedAt() != null ? dtf.format(session.getFinishedAt().atZone(java.time.ZoneId.systemDefault())) : "";

        if (scans.isEmpty()) {
            csv.append(session.getId()).append(';')
                    .append(started).append(';')
                    .append(finished).append(';')
                    .append(session.getScannedCount()).append(';')
                    .append("Нет данных").append(";;;;\n");
        } else {
            scans.forEach(scan -> {
                String scannedAt = dtf.format(scan.getScannedAt().atZone(java.time.ZoneId.systemDefault()));
                csv.append(session.getId()).append(';')
                        .append(started).append(';')
                        .append(finished).append(';')
                        .append(session.getScannedCount()).append(';')
                        .append(scannedAt).append(';')
                        .append(sanitize(scan.getInventoryNumber())).append(';')
                        .append(sanitize(scan.getEquipmentName())).append(';')
                        .append(sanitize(scan.getTypeName())).append(';')
                        .append(sanitize(scan.getLocationName())).append(';')
                        .append(sanitize(scan.getAssignedPerson())).append('\n');
            });
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "inventory_session_" + sessionId + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace(";", ",");
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}

package com.kursach.inventory.web.it;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.InventoryScan;
import com.kursach.inventory.domain.InventorySession;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.service.BuildingService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.InventorySessionService;
import com.kursach.inventory.service.LocationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.kursach.inventory.web.it.WarehouseContextController.BUILDING_ID_SESSION_KEY;

@Controller
@RequestMapping("/warehouse/inventory")
public class ItInventoryController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EquipmentService equipmentService;
    private final InventorySessionService sessionService;
    private final LocationService locationService;
    private final BuildingService buildingService;

    public ItInventoryController(EquipmentService equipmentService,
                                 InventorySessionService sessionService,
                                 LocationService locationService,
                                 BuildingService buildingService) {
        this.equipmentService = equipmentService;
        this.sessionService = sessionService;
        this.locationService = locationService;
        this.buildingService = buildingService;
    }

    @GetMapping
    public String page(@RequestParam(value = "locationId", required = false) Long locationId,
                       HttpSession session,
                       Model model) {
        InventorySession activeSession = sessionService.findActive().orElse(null);
        Long buildingId = currentBuildingId(session);
        List<Location> locations = buildingId == null ? List.of() : locationService.listByBuilding(buildingId);
        model.addAttribute("buildings", buildingService.listAll());
        model.addAttribute("selectedBuildingId", buildingId);
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

        List<EquipmentItem> locationItems = equipmentService.listByLocation(selectedLocationId);
        List<InventoryScan> activeScans = activeSession == null ? List.of() : sessionService.findScans(activeSession.getId());
        Long inventoryLocationId = activeSession != null && activeSession.getLocation() != null
                ? activeSession.getLocation().getId()
                : selectedLocationId;
        Set<Long> scannedOnLocationIds = activeScans.stream()
                .map(InventoryScan::getEquipment)
                .filter(item -> item.getLocation() != null
                        && item.getLocation().getId() != null
                        && item.getLocation().getId().equals(inventoryLocationId))
                .map(EquipmentItem::getId)
                .collect(Collectors.toSet());
        List<InventoryScan> matchedScans = activeScans.stream()
                .filter(scan -> scan.getEquipment().getLocation() != null
                        && scan.getEquipment().getLocation().getId() != null
                        && scan.getEquipment().getLocation().getId().equals(inventoryLocationId))
                .toList();
        List<EquipmentItem> missingItems = locationItems.stream()
                .filter(item -> !scannedOnLocationIds.contains(item.getId()))
                .toList();
        List<InventoryScan> surplusScans = activeScans.stream()
                .filter(scan -> scan.getEquipment().getLocation() == null
                        || scan.getEquipment().getLocation().getId() == null
                        || !scan.getEquipment().getLocation().getId().equals(inventoryLocationId))
                .toList();

        model.addAttribute("selectedLocationId", selectedLocationId);
        model.addAttribute("currentLocationName", currentLocationName);
        model.addAttribute("locationItems", locationItems);
        model.addAttribute("matchedScans", matchedScans);
        model.addAttribute("missingItems", missingItems);
        model.addAttribute("surplusScans", surplusScans);
        return "it/inventory";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam("code") String code,
                       HttpSession httpSession,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            Long buildingId = currentBuildingId(httpSession);
            if (buildingId == null) {
                throw new IllegalStateException("Сначала выберите корпус справа сверху");
            }
            InventorySession activeSession = sessionService.findActive()
                    .orElseThrow(() -> new IllegalStateException("Сначала начните сессию инвентаризации"));
            if (activeSession.getLocation() == null
                    || activeSession.getLocation().getBuilding() == null
                    || !buildingId.equals(activeSession.getLocation().getBuilding().getId())) {
                throw new IllegalStateException("Активная сессия относится к другому корпусу. Переключите корпус или завершите сессию");
            }
            equipmentService.scanInventoryCode(code, actor(authentication));
            redirectAttributes.addFlashAttribute("message", "Единица " + code + " успешно отсканирована");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/warehouse/inventory";
    }

    @PostMapping("/start")
    public String startSession(@RequestParam(value = "locationId", required = false) Long locationId,
                               HttpSession httpSession,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (locationId == null) {
            redirectAttributes.addFlashAttribute("error", "Выберите локацию для инвентаризации");
            return "redirect:/warehouse/inventory";
        }
        try {
            Long buildingId = currentBuildingId(httpSession);
            if (buildingId == null) {
                throw new IllegalArgumentException("Сначала выберите корпус справа сверху");
            }
            Location location = locationService.getById(locationId);
            if (location.getBuilding() == null || !buildingId.equals(location.getBuilding().getId())) {
                throw new IllegalArgumentException("Выбранный кабинет не относится к текущему корпусу");
            }
            InventorySession session = sessionService.startSession(actor(authentication), locationId);
            redirectAttributes.addFlashAttribute("message", "Сессия #" + session.getId() + " начата");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addAttribute("locationId", locationId);
            return "redirect:/warehouse/inventory";
        }
        redirectAttributes.addAttribute("locationId", locationId);
        return "redirect:/warehouse/inventory";
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
        return "redirect:/warehouse/inventory";
    }

    @GetMapping("/export/{sessionId}")
    public ResponseEntity<byte[]> export(@PathVariable Long sessionId) {
        InventorySession session = sessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));
        List<InventoryScan> scans = sessionService.findScans(sessionId);
        Long sessionLocationId = session.getLocation() != null ? session.getLocation().getId() : null;
        String sessionLocationName = session.getLocation() != null ? session.getLocation().getName() : "-";
        String sessionBuildingName = session.getLocation() != null && session.getLocation().getBuilding() != null
                ? session.getLocation().getBuilding().getName()
                : "-";
        List<EquipmentItem> expectedItems = equipmentService.listByLocation(sessionLocationId);
        Map<Long, InventoryScan> matchedScanByEquipmentId = scans.stream()
                .filter(scan -> scan.getEquipment() != null
                        && scan.getEquipment().getId() != null
                        && scan.getEquipment().getLocation() != null
                        && scan.getEquipment().getLocation().getId() != null
                        && scan.getEquipment().getLocation().getId().equals(sessionLocationId))
                .collect(Collectors.toMap(
                        scan -> scan.getEquipment().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));
        List<InventoryScan> surplusScans = scans.stream()
                .filter(scan -> scan.getEquipment() == null
                        || scan.getEquipment().getLocation() == null
                        || scan.getEquipment().getLocation().getId() == null
                        || !scan.getEquipment().getLocation().getId().equals(sessionLocationId))
                .toList();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        ZoneId zone = ZoneId.systemDefault();
        String started = session.getStartedAt() != null ? dtf.format(session.getStartedAt().atZone(zone)) : "";
        String finished = session.getFinishedAt() != null ? dtf.format(session.getFinishedAt().atZone(zone)) : "";
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Сессия", "Начато", "Завершено", "Корпус инвентаризации", "Локация инвентаризации",
                "Статус", "Дата скана", "Инвентарный номер", "Название", "Тип", "Корпус по учету",
                "Где числится по учету", "Ответственный"));

        expectedItems.forEach(item -> {
            InventoryScan scan = matchedScanByEquipmentId.get(item.getId());
            String status = scan == null ? "Не найдено" : "Найдено";
            String scannedAt = scan == null ? "" : dtf.format(scan.getScannedAt().atZone(zone));
            addInventoryReportRow(rows, session, started, finished, sessionBuildingName, sessionLocationName,
                    status, scannedAt, item.getInventoryNumber(), item.getName(),
                    item.getType() != null ? item.getType().getName() : "-",
                    item.getLocation() != null && item.getLocation().getBuilding() != null ? item.getLocation().getBuilding().getName() : "-",
                    item.getLocation() != null ? item.getLocation().getName() : "-",
                    item.getAssignedTo() != null ? item.getAssignedTo().getFullName() : "-");
        });

        surplusScans.forEach(scan -> {
            EquipmentItem item = scan.getEquipment();
            String scannedAt = dtf.format(scan.getScannedAt().atZone(zone));
            String accountingBuilding = item != null && item.getLocation() != null && item.getLocation().getBuilding() != null
                    ? item.getLocation().getBuilding().getName()
                    : "-";
            addInventoryReportRow(rows, session, started, finished, sessionBuildingName, sessionLocationName,
                    "Излишек", scannedAt, scan.getInventoryNumber(), scan.getEquipmentName(), scan.getTypeName(),
                    accountingBuilding, scan.getLocationName(), scan.getAssignedPerson());
        });

        if (expectedItems.isEmpty() && surplusScans.isEmpty()) {
            addInventoryReportRow(rows, session, started, finished, sessionBuildingName, sessionLocationName,
                    "Нет данных", "", "", "", "", "", "", "");
        }

        String filename = "inventory_session_" + sessionId + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(XLSX_MEDIA_TYPE)
                .body(buildXlsx(rows));
    }

    private void addInventoryReportRow(List<List<String>> rows,
                                       InventorySession session,
                                       String started,
                                       String finished,
                                       String sessionBuildingName,
                                       String sessionLocationName,
                                       String status,
                                       String scannedAt,
                                       String inventoryNumber,
                                       String equipmentName,
                                       String typeName,
                                       String accountingBuildingName,
                                       String locationName,
                                       String assignedPerson) {
        rows.add(List.of(
                String.valueOf(session.getId()),
                nullToEmpty(started),
                nullToEmpty(finished),
                nullToEmpty(sessionBuildingName),
                nullToEmpty(sessionLocationName),
                nullToEmpty(status),
                nullToEmpty(scannedAt),
                nullToEmpty(inventoryNumber),
                nullToEmpty(equipmentName),
                nullToEmpty(typeName),
                nullToEmpty(accountingBuildingName),
                nullToEmpty(locationName),
                nullToEmpty(assignedPerson)
        ));
    }

    private byte[] buildXlsx(List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);
            addZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Инвентаризация" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            addZipEntry(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                      <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                      <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                      <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                      <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/></cellXfs>
                    </styleSheet>
                    """);
            addZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(rows));
            zip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String buildSheetXml(List<List<String>> rows) {
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] widths = new int[columnCount];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], safeLength(row.get(i)));
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<cols>");
        for (int i = 0; i < columnCount; i++) {
            double width = Math.min(Math.max(widths[i] + 2, 10), 55);
            xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int excelRow = rowIndex + 1;
            xml.append("<row r=\"").append(excelRow).append("\">");
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                xml.append("<c r=\"").append(columnName(colIndex)).append(excelRow)
                        .append("\" t=\"inlineStr\"");
                if (rowIndex == 0) {
                    xml.append(" s=\"1\"");
                }
                xml.append("><is><t>").append(escapeXml(row.get(colIndex))).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String columnName(int zeroBasedIndex) {
        StringBuilder name = new StringBuilder();
        int index = zeroBasedIndex;
        do {
            name.insert(0, (char) ('A' + (index % 26)));
            index = index / 26 - 1;
        } while (index >= 0);
        return name.toString();
    }

    private String escapeXml(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private Long currentBuildingId(HttpSession session) {
        Object value = session.getAttribute(BUILDING_ID_SESSION_KEY);
        return value instanceof Long id ? id : null;
    }
}

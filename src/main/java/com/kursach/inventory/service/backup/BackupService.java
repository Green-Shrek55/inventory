package com.kursach.inventory.service.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kursach.inventory.repo.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal backup that exports key tables to JSON.
 * (Enough to show backup works; extend later if needed.)
 */
@Service
public class BackupService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final ActionLogRepository actionLogRepository;

    private final ObjectMapper om = new ObjectMapper();

    public BackupService(UserRepository userRepository,
                         DepartmentRepository departmentRepository,
                         EmployeeRepository employeeRepository,
                         EquipmentItemRepository equipmentItemRepository,
                         MaintenanceTicketRepository maintenanceTicketRepository,
                         ActionLogRepository actionLogRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.maintenanceTicketRepository = maintenanceTicketRepository;
        this.actionLogRepository = actionLogRepository;
    }

    public byte[] createZipBackup() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                writeJson(zos, "users.json", userRepository.findAll());
                writeJson(zos, "departments.json", departmentRepository.findAll());
                writeJson(zos, "employees.json", employeeRepository.findAll());
                writeJson(zos, "equipment_items.json", equipmentItemRepository.findAll());
                writeJson(zos, "maintenance_tickets.json", maintenanceTicketRepository.findAll());
                writeJson(zos, "logs.json", actionLogRepository.findAll());
                writeJson(zos, "meta.json", Map.of("format", 1));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Backup failed: " + e.getMessage(), e);
        }
    }

    private void writeJson(ZipOutputStream zos, String name, Object data) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(om.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
        zos.closeEntry();
    }
}

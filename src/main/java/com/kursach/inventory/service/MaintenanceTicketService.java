package com.kursach.inventory.service;

import com.kursach.inventory.domain.*;
import com.kursach.inventory.repo.EmployeeRepository;
import com.kursach.inventory.repo.EquipmentItemRepository;
import com.kursach.inventory.repo.MaintenanceTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
public class MaintenanceTicketService {
    private final MaintenanceTicketRepository ticketRepository;
    private final EquipmentItemRepository equipmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ActionLogService actionLogService;

    public MaintenanceTicketService(MaintenanceTicketRepository ticketRepository,
                                    EquipmentItemRepository equipmentRepository,
                                    EmployeeRepository employeeRepository,
                                    ActionLogService actionLogService) {
        this.ticketRepository = ticketRepository;
        this.equipmentRepository = equipmentRepository;
        this.employeeRepository = employeeRepository;
        this.actionLogService = actionLogService;
    }

    public List<MaintenanceTicket> listActive() {
        return ticketRepository.findByStatusInOrderByUpdatedAtDesc(
                EnumSet.of(MaintenanceStatus.NEW, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.WAITING_PARTS)
        );
    }

    public List<MaintenanceTicket> latest() {
        return ticketRepository.findTop10ByOrderByUpdatedAtDesc();
    }

    public MaintenanceTicket getById(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Заявка не найдена"));
    }

    @Transactional
    public MaintenanceTicket create(Long equipmentId,
                                    String title,
                                    String description,
                                    Long assigneeId,
                                    String actor) {
        EquipmentItem equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Оборудование не найдено"));
        MaintenanceTicket ticket = new MaintenanceTicket();
        ticket.setEquipment(equipment);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setAssignee(resolveAssignee(assigneeId));
        ticket.setCreatedBy(actor);
        ticket.setLastUpdatedBy(actor);
        MaintenanceTicket saved = ticketRepository.save(ticket);
        actionLogService.log(actor, "Создана заявка #" + saved.getId() + " для " + equipment.getInventoryNumber());
        return saved;
    }

    @Transactional
    public MaintenanceTicket updateStatus(Long id,
                                          MaintenanceStatus status,
                                          String resolutionNote,
                                          Long assigneeId,
                                          String actor) {
        MaintenanceTicket ticket = getById(id);
        ticket.setStatus(status);
        ticket.setResolutionNote(resolutionNote);
        ticket.setAssignee(resolveAssignee(assigneeId));
        ticket.setUpdatedAt(Instant.now());
        ticket.setLastUpdatedBy(actor);
        if (status == MaintenanceStatus.COMPLETED || status == MaintenanceStatus.DECLINED) {
            ticket.setClosedAt(Instant.now());
        }
        MaintenanceTicket saved = ticketRepository.save(ticket);
        actionLogService.log(actor, "Изменен статус заявки #" + saved.getId() + " на " + status);
        return saved;
    }

    private Employee resolveAssignee(Long id) {
        if (id == null) return null;
        return employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Исполнитель не найден"));
    }
}

package com.kursach.inventory.web.it;

import com.kursach.inventory.domain.MaintenanceStatus;
import com.kursach.inventory.service.EmployeeService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.MaintenanceTicketService;
import com.kursach.inventory.web.dto.MaintenanceStatusForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/it")
public class ItDashboardController {
    private final EquipmentService equipmentService;
    private final MaintenanceTicketService ticketService;
    private final EmployeeService employeeService;

    public ItDashboardController(EquipmentService equipmentService,
                                 MaintenanceTicketService ticketService,
                                 EmployeeService employeeService) {
        this.equipmentService = equipmentService;
        this.ticketService = ticketService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("activeEquipment", equipmentService.listActive());
        model.addAttribute("archivedEquipment", equipmentService.listArchived());
        model.addAttribute("warehouseEquipment", equipmentService.listWarehouse());
        model.addAttribute("tickets", ticketService.listActive());
        model.addAttribute("latestTickets", ticketService.latest());
        model.addAttribute("statusForm", new MaintenanceStatusForm());
        model.addAttribute("statuses", MaintenanceStatus.values());
        model.addAttribute("employees", employeeService.listAll());
        model.addAttribute("isAdmin", hasRole(authentication, "ROLE_ADMIN"));
        return "it/dashboard";
    }

    @PostMapping("/tickets/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @Valid @ModelAttribute("statusForm") MaintenanceStatusForm form,
                               BindingResult bindingResult,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Не удалось обновить статус: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/it";
        }
        ticketService.updateStatus(id, form.getStatus(), form.getResolutionNote(), form.getAssigneeId(), actor(authentication));
        redirectAttributes.addFlashAttribute("message", "Статус обновлен");
        return "redirect:/it";
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}

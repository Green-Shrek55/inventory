package com.kursach.inventory.web.it;

import com.kursach.inventory.service.EmployeeService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.MaintenanceTicketService;
import com.kursach.inventory.web.dto.MaintenanceTicketForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/it/maintenance")
public class MaintenanceTicketController {

    private final MaintenanceTicketService ticketService;
    private final EquipmentService equipmentService;
    private final EmployeeService employeeService;

    public MaintenanceTicketController(MaintenanceTicketService ticketService,
                                       EquipmentService equipmentService,
                                       EmployeeService employeeService) {
        this.ticketService = ticketService;
        this.equipmentService = equipmentService;
        this.employeeService = employeeService;
    }

    @GetMapping("/new")
    public String newTicket(@ModelAttribute("form") MaintenanceTicketForm form,
                            Model model) {
        model.addAttribute("equipment", equipmentService.listActive());
        model.addAttribute("employees", employeeService.listAll());
        return "it/maintenance-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") MaintenanceTicketForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("equipment", equipmentService.listActive());
            model.addAttribute("employees", employeeService.listAll());
            return "it/maintenance-form";
        }
        ticketService.create(form.getEquipmentId(), form.getTitle(), form.getDescription(), form.getAssigneeId(), actor(authentication));
        redirectAttributes.addFlashAttribute("message", "Заявка создана");
        return "redirect:/it";
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}

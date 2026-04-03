package com.kursach.inventory.web.it;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.service.EmployeeService;
import com.kursach.inventory.service.EquipmentService;
import com.kursach.inventory.service.EquipmentTypeService;
import com.kursach.inventory.service.LocationService;
import com.kursach.inventory.web.dto.EquipmentItemForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
@Controller
@RequestMapping("/it/equipment")
public class ItEquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentTypeService typeService;
    private final LocationService locationService;
    private final EmployeeService employeeService;

    public ItEquipmentController(EquipmentService equipmentService,
                                 EquipmentTypeService typeService,
                                 LocationService locationService,
                                 EmployeeService employeeService) {
        this.equipmentService = equipmentService;
        this.typeService = typeService;
        this.locationService = locationService;
        this.employeeService = employeeService;
    }

    @ModelAttribute("warehouseLocationId")
    public Long warehouseLocationId() {
        return locationService.listAll().stream()
                .filter(loc -> "склад".equalsIgnoreCase(loc.getName()))
                .map(Location::getId)
                .findFirst()
                .orElse(null);
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("activeEquipment", equipmentService.listActive());
        model.addAttribute("warehouseEquipment", equipmentService.listWarehouse());
        model.addAttribute("archivedEquipment", equipmentService.listArchived());
        model.addAttribute("locations", locationService.listAll());
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
        return "redirect:/it/equipment";
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
        return "redirect:/it/equipment";
    }

    @PostMapping("/{id}/placement")
    public String updatePlacement(@PathVariable Long id,
                                  @RequestParam Long locationId,
                                  @RequestParam(required = false) Long employeeId,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {
        try {
            equipmentService.updatePlacement(id, locationId, employeeId, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Перемещение выполнено");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/it/equipment";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          Authentication auth,
                          RedirectAttributes redirectAttributes) {
        equipmentService.archive(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Перемещено в архив");
        return "redirect:/it/equipment";
    }

    @PostMapping("/{id}/unarchive")
    public String unarchive(@PathVariable Long id,
                            Authentication auth,
                            RedirectAttributes redirectAttributes) {
        equipmentService.unarchive(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Возвращено в актив");
        return "redirect:/it/equipment";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication auth,
                         RedirectAttributes redirectAttributes) {
        equipmentService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Оборудование удалено");
        return "redirect:/it/equipment";
    }

    private void populateReferenceData(Model model) {
        model.addAttribute("types", typeService.listAll());
        model.addAttribute("locations", locationService.listAll());
        model.addAttribute("employees", employeeService.listAll());
    }

    private String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}

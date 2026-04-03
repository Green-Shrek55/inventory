package com.kursach.inventory.web.admin;

import com.kursach.inventory.domain.Department;
import com.kursach.inventory.domain.EquipmentType;
import com.kursach.inventory.domain.Location;
import com.kursach.inventory.service.*;
import com.kursach.inventory.web.dto.EmployeeForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/directories")
public class AdminDirectoryController {

    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final LocationService locationService;
    private final EquipmentTypeService typeService;

    public AdminDirectoryController(DepartmentService departmentService,
                                    EmployeeService employeeService,
                                    LocationService locationService,
                                    EquipmentTypeService typeService) {
        this.departmentService = departmentService;
        this.employeeService = employeeService;
        this.locationService = locationService;
        this.typeService = typeService;
    }

    @GetMapping
    public String directories(Model model) {
        prepareModel(model);
        return "admin/directories";
    }

    @PostMapping("/departments")
    public String saveDepartment(@Valid @ModelAttribute("departmentForm") Department department,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareModel(model);
            model.addAttribute("activeTab", "departments");
            return "admin/directories";
        }
        departmentService.save(department, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Отдел сохранен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/departments/{id}/rename")
    public String renameDepartment(@PathVariable Long id,
                                   @RequestParam String name,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        departmentService.rename(id, name, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Отдел обновлен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/departments/{id}/delete")
    public String deleteDepartment(@PathVariable Long id,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        departmentService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Отдел удален");
        return "redirect:/admin/directories";
    }

    @PostMapping("/employees")
    public String saveEmployee(@Valid @ModelAttribute("employeeForm") EmployeeForm form,
                               BindingResult bindingResult,
                               Authentication auth,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareModel(model);
            model.addAttribute("activeTab", "employees");
            return "admin/directories";
        }
        employeeService.save(form.getFullName(), form.getDepartmentId(), actor(auth));
        redirectAttributes.addFlashAttribute("message", "Сотрудник сохранен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/employees/{id}")
    public String updateEmployee(@PathVariable Long id,
                                 @RequestParam String fullName,
                                 @RequestParam Long departmentId,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        employeeService.update(id, fullName, departmentId, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Сотрудник обновлен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/employees/{id}/delete")
    public String deleteEmployee(@PathVariable Long id,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        employeeService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Сотрудник удален");
        return "redirect:/admin/directories";
    }

    @PostMapping("/locations")
    public String saveLocation(@Valid @ModelAttribute("locationForm") Location location,
                               BindingResult bindingResult,
                               Authentication auth,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareModel(model);
            model.addAttribute("activeTab", "locations");
            return "admin/directories";
        }
        locationService.save(location, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Локация сохранена");
        return "redirect:/admin/directories";
    }

    @PostMapping("/locations/{id}/rename")
    public String renameLocation(@PathVariable Long id,
                                 @RequestParam String name,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        locationService.rename(id, name, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Локация обновлена");
        return "redirect:/admin/directories";
    }

    @PostMapping("/locations/{id}/delete")
    public String deleteLocation(@PathVariable Long id,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        locationService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Локация удалена");
        return "redirect:/admin/directories";
    }

    @PostMapping("/types")
    public String saveType(@Valid @ModelAttribute("typeForm") EquipmentType type,
                           BindingResult bindingResult,
                           Authentication auth,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareModel(model);
            model.addAttribute("activeTab", "types");
            return "admin/directories";
        }
        typeService.save(type, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Тип оборудования сохранен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/types/{id}/rename")
    public String renameType(@PathVariable Long id,
                             @RequestParam String name,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        typeService.rename(id, name, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Тип оборудования обновлен");
        return "redirect:/admin/directories";
    }

    @PostMapping("/types/{id}/delete")
    public String deleteType(@PathVariable Long id,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        typeService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Тип оборудования удален");
        return "redirect:/admin/directories";
    }

    private void prepareModel(Model model) {
        model.addAttribute("departments", departmentService.listAll());
        model.addAttribute("employees", employeeService.listAll());
        model.addAttribute("locations", locationService.listAll());
        model.addAttribute("equipmentTypes", typeService.listAll());
        if (!model.containsAttribute("departmentForm")) {
            model.addAttribute("departmentForm", new Department());
        }
        if (!model.containsAttribute("locationForm")) {
            model.addAttribute("locationForm", new Location());
        }
        if (!model.containsAttribute("typeForm")) {
            model.addAttribute("typeForm", new EquipmentType());
        }
        if (!model.containsAttribute("employeeForm")) {
            model.addAttribute("employeeForm", new EmployeeForm());
        }
    }

    private String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}

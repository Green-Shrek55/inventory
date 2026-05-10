package com.kursach.inventory.web.admin;

import com.kursach.inventory.domain.EquipmentType;
import com.kursach.inventory.domain.LocationType;
import com.kursach.inventory.service.BuildingService;
import com.kursach.inventory.service.EquipmentTypeService;
import com.kursach.inventory.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/directories")
public class AdminDirectoryController {

    private final BuildingService buildingService;
    private final LocationService locationService;
    private final EquipmentTypeService typeService;

    public AdminDirectoryController(BuildingService buildingService,
                                    LocationService locationService,
                                    EquipmentTypeService typeService) {
        this.buildingService = buildingService;
        this.locationService = locationService;
        this.typeService = typeService;
    }

    @GetMapping
    public String directories(Model model) {
        prepareModel(model);
        return "admin/directories";
    }

    @PostMapping("/buildings")
    public String saveBuilding(@RequestParam String name,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            buildingService.save(name, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Корпус сохранен");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/directories#buildings";
    }

    @PostMapping("/buildings/{id}/rename")
    public String renameBuilding(@PathVariable Long id,
                                 @RequestParam String name,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            buildingService.rename(id, name, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Корпус обновлен");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/directories#buildings";
    }

    @PostMapping("/buildings/{id}/delete")
    public String deleteBuilding(@PathVariable Long id,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            buildingService.deleteCascade(id, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Корпус и его локации удалены");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Нельзя удалить корпус: он связан с учетными данными.");
        }
        return "redirect:/admin/directories#buildings";
    }

    @PostMapping("/locations")
    public String saveLocation(@RequestParam Long buildingId,
                               @RequestParam String name,
                               @RequestParam LocationType type,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            locationService.save(buildingId, name, type, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Локация сохранена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/directories#locations";
    }

    @PostMapping("/locations/bulk-cabinets")
    public String saveCabinetRange(@RequestParam Long buildingId,
                                   @RequestParam int from,
                                   @RequestParam int to,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        try {
            int created = locationService.createCabinetRange(buildingId, from, to, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Создано кабинетов: " + created);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/directories#locations";
    }

    @PostMapping("/locations/delete-cabinets")
    public String deleteCabinetRange(@RequestParam Long buildingId,
                                     @RequestParam int from,
                                     @RequestParam int to,
                                     Authentication auth,
                                     RedirectAttributes redirectAttributes) {
        try {
            int deleted = locationService.deleteEmptyCabinetRange(buildingId, from, to, actor(auth));
            redirectAttributes.addFlashAttribute("message",
                    "Удалено пустых кабинетов: " + deleted + ". Занятые кабинеты и кабинеты с активной инвентаризацией оставлены.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Удаление не выполнено: часть кабинетов связана с учетными данными.");
        }
        return "redirect:/admin/directories#locations";
    }

    @PostMapping("/locations/{id}/update")
    public String updateLocation(@PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam Long buildingId,
                                 @RequestParam LocationType type,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            locationService.update(id, name, buildingId, type, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Локация обновлена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/directories#locations";
    }

    @PostMapping("/locations/{id}/delete")
    public String deleteLocation(@PathVariable Long id,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            locationService.delete(id, actor(auth));
            redirectAttributes.addFlashAttribute("message", "Локация удалена");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Нельзя удалить локацию: она связана с учетными данными.");
        }
        return "redirect:/admin/directories#locations";
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
        return "redirect:/admin/directories#types";
    }

    @PostMapping("/types/{id}/rename")
    public String renameType(@PathVariable Long id,
                             @RequestParam String name,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        typeService.rename(id, name, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Тип оборудования обновлен");
        return "redirect:/admin/directories#types";
    }

    @PostMapping("/types/{id}/delete")
    public String deleteType(@PathVariable Long id,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        typeService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "Тип оборудования удален");
        return "redirect:/admin/directories#types";
    }

    private void prepareModel(Model model) {
        model.addAttribute("buildings", buildingService.listAll());
        model.addAttribute("locations", locationService.listAll());
        model.addAttribute("locationTypes", LocationType.values());
        model.addAttribute("equipmentTypes", typeService.listAll());
        if (!model.containsAttribute("typeForm")) {
            model.addAttribute("typeForm", new EquipmentType());
        }
    }

    private String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}

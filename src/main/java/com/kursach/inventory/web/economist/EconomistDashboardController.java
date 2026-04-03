package com.kursach.inventory.web.economist;

import com.kursach.inventory.service.EquipmentAnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/economist")
public class EconomistDashboardController {

    private final EquipmentAnalyticsService analyticsService;

    public EconomistDashboardController(EquipmentAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("totalValue", analyticsService.totalValue());
        model.addAttribute("activeValue", analyticsService.activeValue());
        model.addAttribute("archivedValue", analyticsService.archivedValue());
        model.addAttribute("valueByDepartment", analyticsService.valueByDepartment());
        model.addAttribute("valueByLocation", analyticsService.valueByLocation());
        model.addAttribute("recentPurchases", analyticsService.recentPurchases(5));
        model.addAttribute("mostExpensive", analyticsService.mostExpensive(5));
        model.addAttribute("isAdmin", authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        return "economist/dashboard";
    }
}

package com.kursach.inventory.web;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.service.ActionLogService;
import com.kursach.inventory.service.EquipmentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final EquipmentService equipmentService;
    private final ActionLogService actionLogService;

    public HomeController(EquipmentService equipmentService, ActionLogService actionLogService) {
        this.equipmentService = equipmentService;
        this.actionLogService = actionLogService;
    }

    @GetMapping("/")
    public String home(Authentication auth, Model model) {
        List<EquipmentItem> latestItems = equipmentService.listActive()
                .stream()
                .limit(10)
                .toList();
        model.addAttribute("username", auth.getName());
        model.addAttribute("roles", auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        model.addAttribute("recentEquipment", latestItems);
        boolean admin = hasRole(auth, "ROLE_ADMIN");
        boolean it = hasRole(auth, "ROLE_IT");
        boolean economist = hasRole(auth, "ROLE_ECONOMIST");
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isIt", it);
        model.addAttribute("isEconomist", economist);
        if (admin) {
            model.addAttribute("recentLogs", actionLogService.latest(5));
        }
        return "index";
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}

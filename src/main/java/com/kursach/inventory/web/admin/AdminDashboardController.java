package com.kursach.inventory.web.admin;

import com.kursach.inventory.service.ActionLogService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final ActionLogService actionLogService;

    public AdminDashboardController(ActionLogService actionLogService) {
        this.actionLogService = actionLogService;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "actor", required = false) String actor,
                            @RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            @RequestParam(value = "size", defaultValue = "20") int size,
                            Model model) {
        Page<com.kursach.inventory.domain.ActionLog> logPage = actionLogService.search(actor, query, page, size);
        model.addAttribute("logPage", logPage);
        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("actors", actionLogService.actors());
        model.addAttribute("selectedActor", actor);
        model.addAttribute("searchQuery", query);
        return "admin/dashboard";
    }
}

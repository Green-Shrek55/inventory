package com.kursach.inventory.web.admin;

import com.kursach.inventory.service.ActionLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/logs")
public class AdminLogController {

    private final ActionLogService actionLogService;

    public AdminLogController(ActionLogService actionLogService) {
        this.actionLogService = actionLogService;
    }

    @GetMapping
    public String list(Model model) {
        return "redirect:/admin";
    }
}

package com.kursach.inventory.web.economist;

import com.kursach.inventory.service.EquipmentAnalyticsService;
import com.kursach.inventory.service.EquipmentTypeService;
import com.kursach.inventory.service.ReceiptRequestService;
import com.kursach.inventory.service.BuildingService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/economist")
public class EconomistDashboardController {

    private final EquipmentAnalyticsService analyticsService;
    private final EquipmentTypeService typeService;
    private final ReceiptRequestService receiptRequestService;
    private final BuildingService buildingService;

    public EconomistDashboardController(EquipmentAnalyticsService analyticsService,
                                        EquipmentTypeService typeService,
                                        ReceiptRequestService receiptRequestService,
                                        BuildingService buildingService) {
        this.analyticsService = analyticsService;
        this.typeService = typeService;
        this.receiptRequestService = receiptRequestService;
        this.buildingService = buildingService;
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
        model.addAttribute("equipmentTypes", typeService.listAll());
        model.addAttribute("buildings", buildingService.listAll());
        model.addAttribute("receiptRequests", receiptRequestService.listAll());
        model.addAttribute("isAdmin", authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        return "economist/dashboard";
    }

    @PostMapping("/receipt-requests")
    public String createReceiptRequest(@RequestParam(required = false) String title,
                                       @RequestParam(required = false) String supplier,
                                       @RequestParam(required = false) Long buildingId,
                                       @RequestParam(value = "typeIds", required = false) List<Long> typeIds,
                                       @RequestParam(value = "equipmentNames", required = false) List<String> equipmentNames,
                                       @RequestParam(value = "quantities", required = false) List<Integer> quantities,
                                       @RequestParam(value = "prices", required = false) List<BigDecimal> prices,
                                       @RequestParam(value = "purchaseDates", required = false) List<String> purchaseDates,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        try {
            receiptRequestService.create(title, supplier, buildingId,
                    buildLines(typeIds, equipmentNames, quantities, prices, purchaseDates),
                    authentication == null ? "system" : authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Заявка отправлена на приемку");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/economist#receipt-requests";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public String handleBadRequest(Exception ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Проверьте заполнение формы: корпус, тип, количество и цена должны быть указаны корректно");
        return "redirect:/economist#receipt-requests";
    }

    private List<ReceiptRequestService.ReceiptRequestLine> buildLines(List<Long> typeIds,
                                                                       List<String> equipmentNames,
                                                                       List<Integer> quantities,
                                                                       List<BigDecimal> prices,
                                                                       List<String> purchaseDates) {
        List<ReceiptRequestService.ReceiptRequestLine> lines = new ArrayList<>();
        if (typeIds == null || typeIds.isEmpty()) {
            throw new IllegalArgumentException("Добавьте хотя бы одну позицию техники");
        }
        for (int i = 0; i < typeIds.size(); i++) {
            Integer quantity = get(quantities, i, 0);
            BigDecimal price = get(prices, i, BigDecimal.ZERO);
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Количество в каждой позиции должно быть больше нуля");
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Цена не может быть отрицательной");
            }
            lines.add(new ReceiptRequestService.ReceiptRequestLine(
                    typeIds.get(i),
                    get(equipmentNames, i, ""),
                    quantity,
                    price,
                    parseDate(get(purchaseDates, i, ""))
            ));
        }
        return lines;
    }

    private <T> T get(List<T> values, int index, T fallback) {
        return values != null && index < values.size() ? values.get(index) : fallback;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Дата закупки указана в неверном формате");
        }
    }
}

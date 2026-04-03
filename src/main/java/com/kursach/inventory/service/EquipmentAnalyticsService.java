package com.kursach.inventory.service;

import com.kursach.inventory.domain.EquipmentItem;
import com.kursach.inventory.repo.EquipmentItemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EquipmentAnalyticsService {
    private final EquipmentItemRepository repository;

    public EquipmentAnalyticsService(EquipmentItemRepository repository) {
        this.repository = repository;
    }

    public BigDecimal totalValue() {
        return repository.findAll().stream()
                .map(EquipmentItem::getPurchasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal activeValue() {
        return repository.findByArchivedFalseOrderByInventoryNumberAsc().stream()
                .map(EquipmentItem::getPurchasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal archivedValue() {
        return repository.findByArchivedTrueOrderByArchivedAtDesc().stream()
                .map(EquipmentItem::getPurchasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> valueByDepartment() {
        return aggregate(repo -> repo.stream()
                .collect(Collectors.groupingBy(item -> item.getAssignedTo() != null && item.getAssignedTo().getDepartment() != null
                                ? item.getAssignedTo().getDepartment().getName()
                                : "Без отдела",
                        Collectors.mapping(EquipmentItem::getPurchasePrice,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public Map<String, BigDecimal> valueByLocation() {
        return aggregate(repo -> repo.stream()
                .collect(Collectors.groupingBy(item -> item.getLocation() != null ? item.getLocation().getName() : "Не указано",
                        Collectors.mapping(EquipmentItem::getPurchasePrice,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)))));
    }

    public List<EquipmentItem> recentPurchases(int limit) {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(EquipmentItem::getPurchaseDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    public List<EquipmentItem> mostExpensive(int limit) {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(EquipmentItem::getPurchasePrice, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private Map<String, BigDecimal> aggregate(java.util.function.Function<List<EquipmentItem>, Map<String, BigDecimal>> supplier) {
        Map<String, BigDecimal> raw = supplier.apply(repository.findAll());
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}

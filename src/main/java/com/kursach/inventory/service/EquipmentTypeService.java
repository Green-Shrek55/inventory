package com.kursach.inventory.service;

import com.kursach.inventory.domain.EquipmentType;
import com.kursach.inventory.repo.EquipmentTypeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EquipmentTypeService {
    private final EquipmentTypeRepository repository;
    private final ActionLogService logService;

    public EquipmentTypeService(EquipmentTypeRepository repository, ActionLogService logService) {
        this.repository = repository;
        this.logService = logService;
    }

    public List<EquipmentType> listAll() {
        return repository.findAll(Sort.by("name"));
    }

    public EquipmentType getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Тип оборудования не найден"));
    }

    @Transactional
    public EquipmentType save(EquipmentType type, String actor) {
        EquipmentType saved = repository.save(type);
        logService.log(actor, "Сохранен тип оборудования " + saved.getName());
        return saved;
    }

    @Transactional
    public EquipmentType rename(Long id, String name, String actor) {
        EquipmentType type = getById(id);
        type.setName(name);
        EquipmentType saved = repository.save(type);
        logService.log(actor, "Переименован тип оборудования " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        EquipmentType type = getById(id);
        repository.delete(type);
        logService.log(actor, "Удален тип оборудования " + type.getName());
    }
}

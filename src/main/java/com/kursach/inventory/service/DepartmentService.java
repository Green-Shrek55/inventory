package com.kursach.inventory.service;

import com.kursach.inventory.domain.Department;
import com.kursach.inventory.repo.DepartmentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final ActionLogService logService;

    public DepartmentService(DepartmentRepository departmentRepository, ActionLogService logService) {
        this.departmentRepository = departmentRepository;
        this.logService = logService;
    }

    public List<Department> listAll() {
        return departmentRepository.findAll(Sort.by("name"));
    }

    public Department getById(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Подразделение не найдено"));
    }

    @Transactional
    public Department save(Department department, String actor) {
        Department saved = departmentRepository.save(department);
        logService.log(actor, "Сохранен / обновлен отдел " + saved.getName());
        return saved;
    }

    @Transactional
    public Department rename(Long id, String name, String actor) {
        Department department = getById(id);
        department.setName(name);
        Department saved = departmentRepository.save(department);
        logService.log(actor, "Переименован отдел в " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        Department department = getById(id);
        departmentRepository.delete(department);
        logService.log(actor, "Удален отдел " + department.getName());
    }
}

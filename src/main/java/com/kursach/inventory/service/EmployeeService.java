package com.kursach.inventory.service;

import com.kursach.inventory.domain.Department;
import com.kursach.inventory.domain.Employee;
import com.kursach.inventory.repo.DepartmentRepository;
import com.kursach.inventory.repo.EmployeeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ActionLogService logService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           ActionLogService logService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.logService = logService;
    }

    public List<Employee> listAll() {
        return employeeRepository.findAll(Sort.by("fullName"));
    }

    public Employee getById(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Сотрудник не найден"));
    }

    @Transactional
    public Employee save(String fullName, Long departmentId, String actor) {
        Employee employee = new Employee(fullName);
        employee.setDepartment(resolveDepartment(departmentId));
        Employee saved = employeeRepository.save(employee);
        logService.log(actor, "Сохранен сотрудник " + saved.getFullName());
        return saved;
    }

    @Transactional
    public Employee update(Long id, String fullName, Long departmentId, String actor) {
        Employee employee = getById(id);
        employee.setFullName(fullName);
        employee.setDepartment(resolveDepartment(departmentId));
        Employee saved = employeeRepository.save(employee);
        logService.log(actor, "Обновлены данные сотрудника " + saved.getFullName());
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        Employee employee = getById(id);
        employeeRepository.delete(employee);
        logService.log(actor, "Удален сотрудник " + employee.getFullName());
    }

    private Department resolveDepartment(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Подразделение не найдено"));
    }
}

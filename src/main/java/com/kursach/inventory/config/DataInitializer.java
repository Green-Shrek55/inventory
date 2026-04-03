package com.kursach.inventory.config;

import com.kursach.inventory.domain.*;
import com.kursach.inventory.repo.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner init(UserRepository userRepo,
                           DepartmentRepository deptRepo,
                           LocationRepository locationRepository,
                           EmployeeRepository employeeRepository,
                           EquipmentTypeRepository typeRepository,
                           EquipmentItemRepository equipmentRepository,
                           PasswordEncoder encoder) {
        return args -> {
            Department itDept = deptRepo.findByNameIgnoreCase("IT")
                    .orElseGet(() -> deptRepo.save(new Department("IT")));
            Department financeDept = deptRepo.findByNameIgnoreCase("Финансовый отдел")
                    .orElseGet(() -> deptRepo.save(new Department("Финансовый отдел")));
            Department procurementDept = deptRepo.findByNameIgnoreCase("Отдел снабжения")
                    .orElseGet(() -> deptRepo.save(new Department("Отдел снабжения")));

            Location office = ensureLocation(locationRepository, "Офис 101");
            Location storage = ensureLocation(locationRepository, "Склад");
            Location meetingRoom = ensureLocation(locationRepository, "Переговорная");

            EquipmentType desktop = ensureType(typeRepository, "Настольный ПК");
            EquipmentType laptop = ensureType(typeRepository, "Ноутбук");
            EquipmentType printer = ensureType(typeRepository, "Принтер");
            EquipmentType monitor = ensureType(typeRepository, "Монитор");

            Employee sysAdmin = ensureEmployee(employeeRepository, "Иванов Иван (системный администратор)", itDept);
            Employee support = ensureEmployee(employeeRepository, "Петров Петр (инженер поддержки)", itDept);
            Employee economist = ensureEmployee(employeeRepository, "Смирнова Анна (экономист)", financeDept);
            Employee supply = ensureEmployee(employeeRepository, "Сидоров Алексей (специалист по снабжению)", procurementDept);

            ensureEquipment(equipmentRepository, "PC-0001", "Рабочая станция Lenovo", desktop, office, sysAdmin,
                    new BigDecimal("125000"), LocalDate.now().minusMonths(6));
            ensureEquipment(equipmentRepository, "NB-0002", "Ноутбук Dell Latitude", laptop, meetingRoom, support,
                    new BigDecimal("98000"), LocalDate.now().minusMonths(3));
            ensureEquipment(equipmentRepository, "PR-0003", "Принтер HP LaserJet", printer, office, economist,
                    new BigDecimal("45000"), LocalDate.now().minusMonths(12));
            ensureEquipment(equipmentRepository, "MN-0004", "Монитор LG 27\"", monitor, storage, null,
                    new BigDecimal("32000"), LocalDate.now().minusMonths(8));
            ensureEquipment(equipmentRepository, "NB-0005", "Ноутбук для снабжения", laptop, office, supply,
                    new BigDecimal("76000"), LocalDate.now().minusMonths(2));

            ensureUser(userRepo, encoder, "admin", "admin", "andrejkuzevonov@gmail.com", Role.ADMIN, itDept);
            ensureUser(userRepo, encoder, "it", "itpass", "leninklitorochek@gmail.com", Role.IT, itDept);
            ensureUser(userRepo, encoder, "economist", "economist", "economist@example.com", Role.ECONOMIST, financeDept);
        };
    }

    private Location ensureLocation(LocationRepository repository, String name) {
        return repository.findAll().stream()
                .filter(l -> l.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> repository.save(new Location(name)));
    }

    private EquipmentType ensureType(EquipmentTypeRepository repository, String name) {
        return repository.findAll().stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> repository.save(new EquipmentType(name)));
    }

    private Employee ensureEmployee(EmployeeRepository repository, String fullName, Department department) {
        return repository.findAll().stream()
                .filter(e -> e.getFullName().equalsIgnoreCase(fullName))
                .findFirst()
                .orElseGet(() -> {
                    Employee employee = new Employee(fullName);
                    employee.setDepartment(department);
                    return repository.save(employee);
                });
    }

    private void ensureEquipment(EquipmentItemRepository repository,
                                 String invNumber,
                                 String name,
                                 EquipmentType type,
                                 Location location,
                                 Employee employee,
                                 BigDecimal price,
                                 LocalDate purchaseDate) {
        repository.findByInventoryNumber(invNumber).orElseGet(() -> {
            EquipmentItem item = new EquipmentItem();
            item.setInventoryNumber(invNumber);
            item.setName(name);
            item.setType(type);
            item.setLocation(location);
            item.setAssignedTo(employee);
            item.setPurchasePrice(price);
            item.setPurchaseDate(purchaseDate);
            return repository.save(item);
        });
    }

    private void ensureUser(UserRepository repository,
                            PasswordEncoder encoder,
                            String username,
                            String password,
                            String email,
                            Role role,
                            Department department) {
        repository.findByUsername(username).orElseGet(() -> {
            AppUser user = new AppUser(username, encoder.encode(password), email, role);
            user.setDepartment(department);
            user.setEnabled(true);
            return repository.save(user);
        });
    }
}

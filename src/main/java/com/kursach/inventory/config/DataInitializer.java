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
                           BuildingRepository buildingRepository,
                           LocationRepository locationRepository,
                           EmployeeRepository employeeRepository,
                           EquipmentTypeRepository typeRepository,
                           EquipmentItemRepository equipmentRepository,
                           PasswordEncoder encoder) {
        return args -> {
            Department adminDept = deptRepo.findByNameIgnoreCase("Администрация")
                    .orElseGet(() -> deptRepo.save(new Department("Администрация")));
            Department financeDept = deptRepo.findByNameIgnoreCase("Финансовый отдел")
                    .orElseGet(() -> deptRepo.save(new Department("Финансовый отдел")));
            Department inventoryDept = deptRepo.findByNameIgnoreCase("Материальный учет")
                    .orElseGet(() -> deptRepo.save(new Department("Материальный учет")));

            Building building1 = ensureBuilding(buildingRepository, "Нахимовский пр. 21");
            Building building2 = ensureBuilding(buildingRepository, "Нежинская ул. 7");

            Location cabinet101 = ensureLocation(locationRepository, "Кабинет 101", building1, LocationType.CABINET);
            Location cabinet205 = ensureLocation(locationRepository, "Кабинет 205", building1, LocationType.CABINET);
            Location storage1 = ensureLocation(locationRepository, "Склад", building1, LocationType.WAREHOUSE);
            ensureLocation(locationRepository, "Склад", building2, LocationType.WAREHOUSE);
            ensureCabinetRange(locationRepository, building1, 1, 300);
            ensureCabinetRange(locationRepository, building2, 1, 300);

            EquipmentType desktop = ensureType(typeRepository, "Настольный ПК");
            EquipmentType laptop = ensureType(typeRepository, "Ноутбук");
            EquipmentType printer = ensureType(typeRepository, "Принтер");
            EquipmentType monitor = ensureType(typeRepository, "Монитор");

            Employee inventoryOwner = ensureEmployee(employeeRepository, "Иванов Иван (материально ответственное лицо)", inventoryDept);
            Employee economist = ensureEmployee(employeeRepository, "Смирнова Анна (экономист)", financeDept);
            Employee supply = ensureEmployee(employeeRepository, "Сидоров Алексей (ответственный за склад)", inventoryDept);

            ensureEquipment(equipmentRepository, "LO-00000001", "Рабочая станция Lenovo", desktop, cabinet101, inventoryOwner,
                    new BigDecimal("125000"), LocalDate.now().minusMonths(6));
            ensureEquipment(equipmentRepository, "LO-00000002", "Ноутбук Dell Latitude", laptop, cabinet205, inventoryOwner,
                    new BigDecimal("98000"), LocalDate.now().minusMonths(3));
            ensureEquipment(equipmentRepository, "LO-00000003", "Принтер HP LaserJet", printer, cabinet101, economist,
                    new BigDecimal("45000"), LocalDate.now().minusMonths(12));
            ensureEquipment(equipmentRepository, "LO-00000004", "Монитор LG 27\"", monitor, storage1, null,
                    new BigDecimal("32000"), LocalDate.now().minusMonths(8));
            ensureEquipment(equipmentRepository, "LO-00000005", "Ноутбук для снабжения", laptop, cabinet101, supply,
                    new BigDecimal("76000"), LocalDate.now().minusMonths(2));

            ensureDemoEquipmentRange(equipmentRepository, laptop, storage1, 40);
            ensureEquipment(equipmentRepository, "LO-00000006", "Legacy Workstation", desktop, cabinet101, inventoryOwner,
                    new BigDecimal("54000"), LocalDate.now().minusYears(4));

            ensureUser(userRepo, encoder, "admin", "admin123", "andrejkuzevonov@gmail.com", Role.ADMIN, adminDept);
            ensureUser(userRepo, encoder, "warehouse", "admin123", "warehouse@example.com", Role.WAREHOUSE, inventoryDept);
            ensureUser(userRepo, encoder, "economist", "admin123", "economist@example.com", Role.ECONOMIST, financeDept);
            ensureUser(userRepo, encoder, "manager", "admin123", "manager@example.com", Role.MANAGER, adminDept);
            resetDemoPasswords(userRepo, encoder);
        };
    }

    private Building ensureBuilding(BuildingRepository repository, String name) {
        return repository.findByNameIgnoreCase(name)
                .orElseGet(() -> repository.save(new Building(name)));
    }

    private Location ensureLocation(LocationRepository repository, String name, Building building, LocationType type) {
        return repository.findAll().stream()
                .filter(l -> l.getName().equalsIgnoreCase(name)
                        && l.getBuilding() != null
                        && l.getBuilding().getId().equals(building.getId())
                        && l.getType() == type)
                .findFirst()
                .orElseGet(() -> repository.save(new Location(name, building, type)));
    }

    private void ensureCabinetRange(LocationRepository repository, Building building, int from, int to) {
        for (int number = from; number <= to; number++) {
            ensureLocation(repository, "Кабинет " + number, building, LocationType.CABINET);
        }
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

    private void ensureDemoEquipmentRange(EquipmentItemRepository repository,
                                          EquipmentType type,
                                          Location location,
                                          int count) {
        for (int number = 1; number <= count; number++) {
            String inventoryNumber = String.format("LO-%08d", 1000 + number);
            String name = String.format("Demo Laptop %02d", number);
            BigDecimal price = new BigDecimal("64000").add(BigDecimal.valueOf(number * 250L));
            LocalDate purchaseDate = LocalDate.now().minusDays(number);
            ensureEquipment(repository, inventoryNumber, name, type, location, null, price, purchaseDate);
        }
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

    private void resetDemoPasswords(UserRepository repository, PasswordEncoder encoder) {
        for (String username : java.util.List.of("admin", "warehouse", "economist", "manager")) {
            repository.findByUsername(username).ifPresent(user -> {
                user.setPasswordHash(encoder.encode("admin123"));
                user.setFailedLoginAttempts(0);
                user.setLoginLockedUntil(null);
                repository.save(user);
            });
        }
    }
}

package com.kursach.inventory.service;

import com.kursach.inventory.domain.Building;
import com.kursach.inventory.domain.EquipmentType;
import com.kursach.inventory.domain.ReceiptRequest;
import com.kursach.inventory.domain.ReceiptRequestItem;
import com.kursach.inventory.domain.ReceiptRequestStatus;
import com.kursach.inventory.repo.BuildingRepository;
import com.kursach.inventory.repo.EquipmentTypeRepository;
import com.kursach.inventory.repo.ReceiptRequestItemRepository;
import com.kursach.inventory.repo.ReceiptRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReceiptRequestService {
    private final ReceiptRequestRepository requestRepository;
    private final ReceiptRequestItemRepository itemRepository;
    private final EquipmentTypeRepository typeRepository;
    private final BuildingRepository buildingRepository;
    private final EquipmentService equipmentService;
    private final ActionLogService logService;

    public ReceiptRequestService(ReceiptRequestRepository requestRepository,
                                 ReceiptRequestItemRepository itemRepository,
                                 EquipmentTypeRepository typeRepository,
                                 BuildingRepository buildingRepository,
                                 EquipmentService equipmentService,
                                 ActionLogService logService) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.typeRepository = typeRepository;
        this.buildingRepository = buildingRepository;
        this.equipmentService = equipmentService;
        this.logService = logService;
    }

    public List<ReceiptRequest> listAll() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ReceiptRequest> listByBuilding(Long buildingId) {
        if (buildingId == null) {
            return List.of();
        }
        return requestRepository.findByBuilding_IdOrderByCreatedAtDesc(buildingId);
    }

    public ReceiptRequest getById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Заявка не найдена"));
    }

    @Transactional
    public ReceiptRequest create(String title,
                                 String supplier,
                                 Long buildingId,
                                 Long typeId,
                                 String equipmentName,
                                 int quantity,
                                 BigDecimal price,
                                 LocalDate purchaseDate,
                                 String actor) {
        return create(title, supplier, buildingId,
                List.of(new ReceiptRequestLine(typeId, equipmentName, quantity, price, purchaseDate)),
                actor);
    }

    @Transactional
    public ReceiptRequest create(String title,
                                 String supplier,
                                 Long buildingId,
                                 List<ReceiptRequestLine> lines,
                                 String actor) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Выберите корпус поставки");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Добавьте хотя бы одну позицию техники");
        }
        for (ReceiptRequestLine line : lines) {
            validateLine(line);
        }

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Корпус поставки не найден"));

        ReceiptRequest request = new ReceiptRequest();
        request.setTitle(title == null || title.isBlank() ? "Заявка на приемку" : title.trim());
        if (supplier == null || supplier.isBlank()) {
            throw new IllegalArgumentException("Укажите поставщика");
        }
        request.setSupplier(supplier.trim());
        request.setBuilding(building);
        request.setCreatedBy(actor);

        ReceiptRequest saved = requestRepository.save(request);
        int inventoryIndex = 1;
        for (ReceiptRequestLine line : lines) {
            EquipmentType type = typeRepository.findById(line.typeId())
                    .orElseThrow(() -> new IllegalArgumentException("Тип техники не найден"));
            for (int i = 0; i < line.quantity(); i++) {
                ReceiptRequestItem item = new ReceiptRequestItem();
                item.setRequest(saved);
                item.setType(type);
                item.setName(line.equipmentName() == null || line.equipmentName().isBlank()
                        ? type.getName()
                        : line.equipmentName().trim());
                item.setExpectedInventoryNumber(buildInventoryNumber(saved.getId(), inventoryIndex++));
                item.setPrice(line.price() == null ? BigDecimal.ZERO : line.price());
                item.setPurchaseDate(line.purchaseDate());
                saved.getItems().add(item);
            }
        }

        saved = requestRepository.save(saved);
        logService.log(actor, "Создана заявка на приемку #" + saved.getId());
        return saved;
    }

    @Transactional
    public ReceiptRequest startAcceptance(Long requestId, String actor) {
        ReceiptRequest request = getById(requestId);
        if (request.getStatus() != ReceiptRequestStatus.SENT_TO_WAREHOUSE) {
            throw new IllegalStateException("Приемку можно начать только для заявки, ожидающей склад");
        }
        request.setStatus(ReceiptRequestStatus.IN_PROGRESS);
        request.setAcceptanceStartedAt(Instant.now());
        request.setAcceptanceStartedBy(actor);
        ReceiptRequest saved = requestRepository.save(request);
        logService.log(actor, "Начата приемка по заявке #" + requestId);
        return saved;
    }

    @Transactional
    public ReceiptRequest finishAcceptance(Long requestId, String actor) {
        ReceiptRequest request = getById(requestId);
        if (request.getStatus() != ReceiptRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Сначала начните приемку по этой заявке");
        }
        boolean allAccepted = request.getItems().stream().allMatch(ReceiptRequestItem::isAccepted);
        boolean anyAccepted = request.getItems().stream().anyMatch(ReceiptRequestItem::isAccepted);
        request.setStatus(allAccepted ? ReceiptRequestStatus.ACCEPTED :
                (anyAccepted ? ReceiptRequestStatus.PARTIALLY_ACCEPTED : ReceiptRequestStatus.NOT_ACCEPTED));
        request.setAcceptanceFinishedAt(Instant.now());
        request.setAcceptanceFinishedBy(actor);
        ReceiptRequest saved = requestRepository.save(request);
        logService.log(actor, "Завершена приемка по заявке #" + requestId);
        return saved;
    }

    @Transactional
    public ReceiptRequest acceptCode(Long requestId, String code, Long warehouseId, String actor) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Введите инвентарный номер / ШК");
        }
        ReceiptRequest request = getById(requestId);
        if (request.getStatus() != ReceiptRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Сначала начните приемку по этой заявке");
        }
        ReceiptRequestItem item = itemRepository
                .findByRequest_IdAndExpectedInventoryNumberIgnoreCase(requestId, code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Такого номера нет в заявке"));
        if (item.isAccepted()) {
            throw new IllegalArgumentException("Эта единица уже принята");
        }

        equipmentService.saveOrUpdate(null,
                item.getExpectedInventoryNumber(),
                item.getName(),
                item.getType().getId(),
                warehouseId,
                null,
                item.getPrice(),
                item.getPurchaseDate(),
                actor);
        item.setAccepted(true);
        itemRepository.save(item);

        request.setStatus(ReceiptRequestStatus.IN_PROGRESS);
        ReceiptRequest saved = requestRepository.save(request);
        logService.log(actor, "Принята единица " + item.getExpectedInventoryNumber() + " по заявке #" + requestId);
        return saved;
    }

    private String buildInventoryNumber(Long requestId, int index) {
        return String.format("LO-%08d%03d", requestId == null ? 0 : requestId, index);
    }

    private void validateLine(ReceiptRequestLine line) {
        if (line == null || line.typeId() == null) {
            throw new IllegalArgumentException("В каждой позиции выберите тип техники");
        }
        if (line.equipmentName() == null || line.equipmentName().isBlank()) {
            throw new IllegalArgumentException("В каждой позиции укажите название или модель техники");
        }
        if (line.quantity() <= 0) {
            throw new IllegalArgumentException("Количество в каждой позиции должно быть больше нуля");
        }
        if (line.price() == null || line.price().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Цена в каждой позиции должна быть не меньше 1 рубля");
        }
        if (line.purchaseDate() == null) {
            throw new IllegalArgumentException("В каждой позиции укажите дату закупки");
        }
        if (line.equipmentName() != null && line.equipmentName().length() > 255) {
            throw new IllegalArgumentException("Название техники слишком длинное");
        }
    }

    public record ReceiptRequestLine(Long typeId,
                                     String equipmentName,
                                     int quantity,
                                     BigDecimal price,
                                     LocalDate purchaseDate) {
    }
}

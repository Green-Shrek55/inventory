package com.kursach.inventory.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "equipment_items")
public class EquipmentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String inventoryNumber;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private EquipmentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee assignedTo;

    @NotNull
    @Column(name = "purchase_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private boolean archived = false;

    // FIX: this field must exist because repository query uses it
    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "last_inventory_scan_at")
    private Instant lastInventoryScanAt;

    public EquipmentItem() {}

    public Long getId() { return id; }
    public String getInventoryNumber() { return inventoryNumber; }
    public String getName() { return name; }
    public EquipmentType getType() { return type; }
    public Location getLocation() { return location; }
    public Employee getAssignedTo() { return assignedTo; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public boolean isArchived() { return archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getLastInventoryScanAt() { return lastInventoryScanAt; }

    public void setId(Long id) { this.id = id; }
    public void setInventoryNumber(String inventoryNumber) { this.inventoryNumber = inventoryNumber; }
    public void setName(String name) { this.name = name; }
    public void setType(EquipmentType type) { this.type = type; }
    public void setLocation(Location location) { this.location = location; }
    public void setAssignedTo(Employee assignedTo) { this.assignedTo = assignedTo; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public void setLastInventoryScanAt(Instant lastInventoryScanAt) { this.lastInventoryScanAt = lastInventoryScanAt; }
}

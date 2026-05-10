package com.kursach.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "disposal_scans",
        uniqueConstraints = @UniqueConstraint(name = "uk_disposal_session_equipment", columnNames = {"session_id", "equipment_id"}))
public class DisposalScan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private DisposalSession session;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private EquipmentItem equipment;

    @Column(nullable = false)
    private Instant scannedAt = Instant.now();

    @Column(nullable = false)
    private String inventoryNumber;

    @Column(nullable = false)
    private String equipmentName;

    @Column(nullable = false)
    private String typeName;

    @Column(nullable = false)
    private String locationName;

    public Long getId() {
        return id;
    }

    public DisposalSession getSession() {
        return session;
    }

    public void setSession(DisposalSession session) {
        this.session = session;
    }

    public EquipmentItem getEquipment() {
        return equipment;
    }

    public void setEquipment(EquipmentItem equipment) {
        this.equipment = equipment;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(Instant scannedAt) {
        this.scannedAt = scannedAt;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
}

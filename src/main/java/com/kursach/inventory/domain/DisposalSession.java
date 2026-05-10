package com.kursach.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "disposal_sessions")
public class DisposalSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    @Column(nullable = false)
    private String startedBy;

    private Instant finishedAt;

    private String finishedBy;

    @Column(length = 128)
    private String sealNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventorySessionStatus status = InventorySessionStatus.ACTIVE;

    @Column(nullable = false)
    private int scannedCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFinishedBy() {
        return finishedBy;
    }

    public void setFinishedBy(String finishedBy) {
        this.finishedBy = finishedBy;
    }

    public String getSealNumber() {
        return sealNumber;
    }

    public void setSealNumber(String sealNumber) {
        this.sealNumber = sealNumber;
    }

    public InventorySessionStatus getStatus() {
        return status;
    }

    public void setStatus(InventorySessionStatus status) {
        this.status = status;
    }

    public int getScannedCount() {
        return scannedCount;
    }

    public void setScannedCount(int scannedCount) {
        this.scannedCount = scannedCount;
    }

    public void incrementScans() {
        this.scannedCount++;
    }

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }
}

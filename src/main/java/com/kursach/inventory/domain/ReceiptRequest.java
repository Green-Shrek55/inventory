package com.kursach.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipt_requests")
public class ReceiptRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptRequestStatus status = ReceiptRequestStatus.SENT_TO_WAREHOUSE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private String createdBy;

    private Instant acceptanceStartedAt;

    private String acceptanceStartedBy;

    private Instant acceptanceFinishedAt;

    private String acceptanceFinishedBy;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptRequestItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSupplier() { return supplier; }
    public Building getBuilding() { return building; }
    public ReceiptRequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getAcceptanceStartedAt() { return acceptanceStartedAt; }
    public String getAcceptanceStartedBy() { return acceptanceStartedBy; }
    public Instant getAcceptanceFinishedAt() { return acceptanceFinishedAt; }
    public String getAcceptanceFinishedBy() { return acceptanceFinishedBy; }
    public List<ReceiptRequestItem> getItems() { return items; }
    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public void setBuilding(Building building) { this.building = building; }
    public void setStatus(ReceiptRequestStatus status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setAcceptanceStartedAt(Instant acceptanceStartedAt) { this.acceptanceStartedAt = acceptanceStartedAt; }
    public void setAcceptanceStartedBy(String acceptanceStartedBy) { this.acceptanceStartedBy = acceptanceStartedBy; }
    public void setAcceptanceFinishedAt(Instant acceptanceFinishedAt) { this.acceptanceFinishedAt = acceptanceFinishedAt; }
    public void setAcceptanceFinishedBy(String acceptanceFinishedBy) { this.acceptanceFinishedBy = acceptanceFinishedBy; }
    public void setItems(List<ReceiptRequestItem> items) { this.items = items; }
}

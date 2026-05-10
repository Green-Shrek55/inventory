package com.kursach.inventory.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "receipt_request_items")
public class ReceiptRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ReceiptRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private EquipmentType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String expectedInventoryNumber;

    @Column(nullable = false)
    private boolean accepted = false;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    private LocalDate purchaseDate;

    public Long getId() { return id; }
    public ReceiptRequest getRequest() { return request; }
    public EquipmentType getType() { return type; }
    public String getName() { return name; }
    public String getExpectedInventoryNumber() { return expectedInventoryNumber; }
    public boolean isAccepted() { return accepted; }
    public BigDecimal getPrice() { return price; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setId(Long id) { this.id = id; }
    public void setRequest(ReceiptRequest request) { this.request = request; }
    public void setType(EquipmentType type) { this.type = type; }
    public void setName(String name) { this.name = name; }
    public void setExpectedInventoryNumber(String expectedInventoryNumber) { this.expectedInventoryNumber = expectedInventoryNumber; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
}

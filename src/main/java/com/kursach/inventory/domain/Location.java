package com.kursach.inventory.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "locations", uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "name", "type"}))
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationType type = LocationType.CABINET;

    public Location() {}
    public Location(String name) { this.name = name; }
    public Location(String name, Building building, LocationType type) {
        this.name = name;
        this.building = building;
        this.type = type;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Building getBuilding() { return building; }
    public LocationType getType() { return type; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setBuilding(Building building) { this.building = building; }
    public void setType(LocationType type) { this.type = type; }

    public String getDisplayName() {
        String buildingName = building == null ? "Корпус не указан" : building.getName();
        return buildingName + ", " + name;
    }
}

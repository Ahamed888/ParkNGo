package com.example.parkngo.facility;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

// [ Entity & Encapsulation]: Represents add-on amenities (e.g., EV Charging, Car Wash) offered at parking facilities.
@Entity
@Table(name = "FacilityService")
public class FacilityService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ServiceID")
    private Long id;

    @NotBlank(message = "Service name is required")
    @Size(min = 2, max = 100, message = "Service name must be between 2 and 100 characters")
    @Column(name = "Name", nullable = false)
    private String name;

    @NotBlank(message = "Service description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price cannot be negative")
    @Digits(integer = 6, fraction = 2, message = "Price format invalid (max 6 digits and 2 decimal places)")
    @Column(name = "Price", nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    // A service can be offered at multiple facilities. The join table is
    // synchronized by DataInitializer and the V20261008_01 migration.
    @ManyToMany
    @JoinTable(
            name = "FacilityServiceFacility",
            joinColumns = @JoinColumn(name = "ServiceID"),
            inverseJoinColumns = @JoinColumn(name = "FacilityID")
    )
    private Set<ParkingFacility> facilities = new LinkedHashSet<>();

    // [ JPA AttributeConverter]: Converts database status strings (ACTIVE/INACTIVE) to ServiceStatus enum values.
    @Convert(converter = ServiceStatusConverter.class)
    @Column(name = "Status", nullable = false)
    private ServiceStatus status = ServiceStatus.AVAILABLE;

    @Column(name = "Deleted", nullable = false)
    private boolean deleted;

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public enum ServiceStatus {
        AVAILABLE, UNAVAILABLE, ACTIVE, INACTIVE
    }

    public FacilityService() {}

    public FacilityService(Long id, String name, String description, BigDecimal price, ParkingFacility facility, ServiceStatus status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        setFacility(facility);
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Set<ParkingFacility> getFacilities() {
        return facilities;
    }

    public void setFacilities(Collection<ParkingFacility> facilities) {
        Set<ParkingFacility> replacement = facilities == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(facilities);
        if (this.facilities == null) {
            this.facilities = replacement;
        } else {
            this.facilities.clear();
            this.facilities.addAll(replacement);
        }
    }

    /**
     * Backwards-compatible primary facility accessor for older read-only views.
     */
    @Transient
    public ParkingFacility getFacility() {
        return facilities.stream().findFirst().orElse(null);
    }

    public void setFacility(ParkingFacility facility) {
        if (facilities == null) {
            facilities = new LinkedHashSet<>();
        } else {
            facilities.clear();
        }
        if (facility != null) {
            facilities.add(facility);
        }
    }

    @Transient
    public boolean belongsToFacility(Long facilityId) {
        if (facilityId == null || facilities == null) {
            return false;
        }
        return facilities.stream().anyMatch(f -> f.getId() != null && f.getId().equals(facilityId));
    }

    @Transient
    public String getFacilityIdsCsv() {
        if (facilities == null || facilities.isEmpty()) {
            return "";
        }
        return facilities.stream()
                .map(ParkingFacility::getId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return !deleted && (status == ServiceStatus.AVAILABLE || status == ServiceStatus.ACTIVE);
    }

    public String getIconClass() {
        if (name == null) return "fa-wrench";
        String lower = name.toLowerCase();
        if (lower.contains("wash")) return "fa-soap";
        if (lower.contains("ev") || lower.contains("charg")) return "fa-bolt";
        if (lower.contains("valet")) return "fa-user-tie";
        if (lower.contains("detail")) return "fa-wand-magic-sparkles";
        if (lower.contains("tire") || lower.contains("tyre") || lower.contains("wheel")) return "fa-circle-notch";
        if (lower.contains("oil") || lower.contains("lube")) return "fa-oil-can";
        if (lower.contains("protect") || lower.contains("guard") || lower.contains("security")) return "fa-shield-halved";
        return "fa-wrench";
    }
}

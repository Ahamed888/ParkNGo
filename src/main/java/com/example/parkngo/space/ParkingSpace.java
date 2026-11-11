package com.example.parkngo.space;

import com.example.parkngo.facility.ParkingFacility;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ParkingSpace")
public class ParkingSpace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SpaceID")
    private Long id;

    @NotBlank
    @Size(min = 2, max = 30, message = "Space code must be between 2 and 30 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]*$", message = "Space code may contain only letters, numbers, spaces, and hyphens")
    @Column(name = "SpaceNumber", nullable = false, unique = true)
    private String spaceNumber;

    @NotNull
    @Column(name = "Floor", nullable = false)
    private Integer floor = 1;

    @NotBlank(message = "Zone is required")
    @Size(max = 30, message = "Zone must be 30 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9]+(?:[- ][A-Za-z0-9]+)*$", message = "Zone may contain only letters, numbers, spaces, and hyphens")
    @Column(name = "Zone")
    private String zone = "ZONE-A";

    @NotBlank(message = "Parking type is required")
    @Pattern(regexp = "^(STANDARD|COMPACT|ELECTRIC|HANDICAPPED|VIP|ACCESSIBLE|EV_CHARGING)$", message = "Please select a supported parking type")
    @Column(name = "ParkingType", nullable = false)
    private String parkingType = "STANDARD"; // STANDARD, COMPACT, ACCESSIBLE, EV_CHARGING

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private ParkingSpaceStatus status = ParkingSpaceStatus.AVAILABLE;

    @NotNull
    @DecimalMin(value = "0.00", message = "Hourly rate cannot be negative")
    @DecimalMax(value = "999999.99", message = "Hourly rate cannot exceed 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Hourly rate must have at most 6 whole digits and 2 decimal places")
    @Column(name = "HourlyRate", nullable = false)
    private BigDecimal hourlyRate = BigDecimal.valueOf(250.00);

    @Column(name = "HasEVCharging", nullable = false)
    private Boolean hasEVCharging = false;

    @Column(name = "IsAccessible", nullable = false)
    private Boolean isAccessible = false;

    @NotBlank(message = "Allocation type is required")
    @Pattern(regexp = "^(GENERAL|EV_PRIORITY|ACCESSIBLE|RESERVED_VIP|LONG_TERM|ONLINE_BOOKABLE|WALKIN_ONLY|BOTH)$", message = "Please select a supported allocation type")
    @Column(name = "AllocationType", nullable = false)
    // SQL Server defines business categories (GENERAL, EV_PRIORITY,
    // ACCESSIBLE, RESERVED_VIP, LONG_TERM). This is not a booking channel.
    private String allocationType = "GENERAL";

    @ManyToOne
    @JoinColumn(name = "FacilityID", nullable = false)
    private ParkingFacility facility;

    // When set, this bay stays treated as RESERVED (excluded from "available
    // to book" listings) until this moment passes, even though it's no
    // longer actually attached to the reservation that used to hold it - a
    // short grace period after a customer moves their booking to a
    // different bay, rather than reopening it to the public instantly.
    // Cleared once ParkingSpaceService's scheduled sweep flips the bay back
    // to AVAILABLE.
    @Column(name = "PendingReleaseAt")
    private LocalDateTime pendingReleaseAt;

    public enum ParkingSpaceStatus {
        AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE, OUT_OF_SERVICE
    }

    public enum AllocationType {
        GENERAL, EV_PRIORITY, ACCESSIBLE, RESERVED_VIP, LONG_TERM, ONLINE_BOOKABLE, WALKIN_ONLY, BOTH;

        public static AllocationType fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return AllocationType.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public ParkingSpace() {}

    public ParkingSpace(Long id, String spaceNumber, Integer floor, String zone, String parkingType, ParkingSpaceStatus status, BigDecimal hourlyRate, Boolean hasEVCharging, Boolean isAccessible, String allocationType, ParkingFacility facility) {
        this.id = id;
        this.spaceNumber = spaceNumber;
        this.floor = floor;
        this.zone = zone;
        this.parkingType = parkingType;
        this.status = status;
        this.hourlyRate = hourlyRate;
        this.hasEVCharging = hasEVCharging;
        this.isAccessible = isAccessible;
        this.allocationType = normalizeAllocationType(allocationType);
        this.facility = facility;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSpaceNumber() {
        return spaceNumber;
    }

    public void setSpaceNumber(String spaceNumber) {
        this.spaceNumber = spaceNumber;
    }

    public Integer getFloor() {
        return floor;
    }

    public Integer getFloorNumber() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getParkingType() {
        return parkingType;
    }

    public void setParkingType(String parkingType) {
        this.parkingType = parkingType;
    }

    public ParkingSpaceStatus getStatus() {
        return status;
    }

    public void setStatus(ParkingSpaceStatus status) {
        this.status = status;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public Boolean getHasEVCharging() {
        return hasEVCharging;
    }

    public void setHasEVCharging(Boolean hasEVCharging) {
        this.hasEVCharging = hasEVCharging;
    }

    public Boolean getIsAccessible() {
        return isAccessible;
    }

    public void setIsAccessible(Boolean isAccessible) {
        this.isAccessible = isAccessible;
    }

    public String getAllocationType() {
        return allocationType;
    }

    public void setAllocationType(String allocationType) {
        this.allocationType = normalizeAllocationType(allocationType);
    }

    public void setAllocationType(AllocationType allocationType) {
        this.allocationType = allocationType != null ? allocationType.name() : "GENERAL";
    }

    private String normalizeAllocationType(String allocationType) {
        return allocationType == null || allocationType.isBlank() ? "GENERAL" : allocationType.trim().toUpperCase();
    }

    public ParkingFacility getFacility() {
        return facility;
    }

    public void setFacility(ParkingFacility facility) {
        this.facility = facility;
    }

    public LocalDateTime getPendingReleaseAt() {
        return pendingReleaseAt;
    }

    public void setPendingReleaseAt(LocalDateTime pendingReleaseAt) {
        this.pendingReleaseAt = pendingReleaseAt;
    }
}

package com.example.parkngo.facility;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.time.LocalDateTime;

// [ Entity & Encapsulation]: Maps to SQL Server table 'ParkingFacility'. Private fields with getters/setters protect data integrity.
@Entity
@Table(name = "ParkingFacility")
public class ParkingFacility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FacilityID")
    private Long id;

    // [VIVA - Bean Validation]: Annotations enforce constraints (@NotBlank, @Size, @Pattern) before data reaches persistence layer.
    @NotBlank(message = "Facility name is required")
    @Size(min = 2, max = 100, message = "Facility name must be between 2 and 100 characters")
    @Column(name = "Name", nullable = false, unique = true)
    private String name;

    @Column(name = "Street", length = 150)
    private String street;

    @Size(max = 80, message = "City must be 80 characters or fewer")
    @Column(name = "City", length = 80)
    private String city;

    @Size(max = 20, message = "Postal code must be 20 characters or fewer")
    @Column(name = "PostalCode", length = 20)
    private String postalCode;

    @NotBlank(message = "Facility location / address is required")
    @Size(min = 10, max = 255, message = "Facility location / address must be at least 10 characters")
    @Column(name = "Address", length = 255)
    private String address;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^\\+94[ -]?(?:[0-9]{9}|[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}|[0-9]{2}[ -]?[0-9]{7}|[0-9]{3}[ -]?[0-9]{3}[ -]?[0-9]{3})$", message = "Invalid contact number. Must start with +94 followed by 9 digits (e.g., +94771234567 or +94-11-2345678)")
    @Column(name = "ContactNumber", nullable = false)
    private String contactNumber;

    @NotNull(message = "Opening time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Column(name = "OpeningTime", nullable = false)
    private LocalTime openingTime = LocalTime.of(6, 0);

    @NotNull(message = "Closing time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    @Column(name = "ClosingTime", nullable = false)
    private LocalTime closingTime = LocalTime.of(23, 0);

    @NotNull(message = "Total capacity is required")
    @Min(value = 1, message = "Total capacity must be at least 1")
    @Max(value = 5000, message = "Total capacity cannot exceed 5000")
    @Column(name = "TotalCapacity", nullable = false)
    private Integer totalCapacity = 100;

    @NotNull(message = "Number of floors is required")
    @Min(value = 1, message = "Total floors must be a whole number between 1 and 10")
    @Max(value = 10, message = "Total floors must be a whole number between 1 and 10")
    @Column(name = "TotalFloors", nullable = false, columnDefinition = "integer default 1")
    private Integer totalFloors = 1;

    @Column(name = "CurrentOccupancy", nullable = false)
    private Integer currentOccupancy = 0;

    // [VIVA - Soft Deactivation]: Status (ACTIVE/INACTIVE) controls customer booking visibility without deleting historical records.
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private FacilityStatus status = FacilityStatus.ACTIVE;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be at least 10 characters")
    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ManagedByAdminID")
    private Long managedByAdminId;

    @Column(name = "ImageUrl", length = 500)
    private String imageUrl;

    public enum FacilityStatus {
        ACTIVE, INACTIVE
    }

    public ParkingFacility() {}

    public ParkingFacility(Long id, String name, String address, String contactNumber, LocalTime openingTime, LocalTime closingTime, Integer totalCapacity, Integer currentOccupancy, String description) {
        this(id, name, address, contactNumber, openingTime, closingTime, totalCapacity, 1, currentOccupancy, FacilityStatus.ACTIVE, description);
    }

    public ParkingFacility(Long id, String name, String address, String contactNumber, LocalTime openingTime, LocalTime closingTime, Integer totalCapacity, Integer currentOccupancy, FacilityStatus status, String description) {
        this(id, name, address, contactNumber, openingTime, closingTime, totalCapacity, 1, currentOccupancy, status, description);
    }

    public ParkingFacility(Long id, String name, String address, String contactNumber, LocalTime openingTime, LocalTime closingTime, Integer totalCapacity, Integer totalFloors, Integer currentOccupancy, FacilityStatus status, String description) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.contactNumber = contactNumber;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.totalCapacity = totalCapacity;
        this.totalFloors = totalFloors != null && totalFloors >= 1 ? totalFloors : 1;
        this.currentOccupancy = currentOccupancy;
        this.status = status != null ? status : FacilityStatus.ACTIVE;
        this.description = description;
    }

    // [VIVA - Cross-Field Validation]: Custom validation ensuring facility closing time is chronologically after opening time.
    @AssertTrue(message = "Closing time must be after opening time")
    public boolean isClosingTimeAfterOpeningTime() {
        if (openingTime == null || closingTime == null) {
            return true;
        }
        return closingTime.isAfter(openingTime);
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

    @PostLoad
    @PrePersist
    @PreUpdate
    public void ensureAddress() {
        if (status == null) {
            status = FacilityStatus.ACTIVE;
        }
        if (totalFloors == null || totalFloors < 1) {
            totalFloors = 1;
        }
        if (currentOccupancy == null) {
            currentOccupancy = 0;
        }

        // Sanitize legacy contact number to valid +94 Sri Lankan format
        if (contactNumber == null || !contactNumber.matches("^\\+94[ -]?(?:[0-9]{9}|[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}|[0-9]{2}[ -]?[0-9]{7}|[0-9]{3}[ -]?[0-9]{3}[ -]?[0-9]{3})$")) {
            if (contactNumber != null && contactNumber.trim().startsWith("0")) {
                String digitsOnly = contactNumber.trim().substring(1).replaceAll("[^0-9]", "");
                if (digitsOnly.length() == 9) {
                    contactNumber = "+94" + digitsOnly;
                } else {
                    contactNumber = "+94112345678";
                }
            } else if (contactNumber != null && !contactNumber.trim().isEmpty() && !contactNumber.startsWith("+94")) {
                String digitsOnly = contactNumber.replaceAll("[^0-9]", "");
                if (digitsOnly.length() == 9) {
                    contactNumber = "+94" + digitsOnly;
                }
            }
        }

        // If address is provided, parse street, city, postal code if they are missing
        if (address != null && !address.trim().isEmpty()) {
            String trimmed = address.trim();
            if ((city == null || city.trim().isEmpty()) || (street == null || street.trim().isEmpty())) {
                String[] parts = trimmed.split(",");
                if (parts.length > 1) {
                    if (street == null || street.trim().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(parts[i].trim());
                        }
                        this.street = sb.toString();
                    }
                    if (city == null || city.trim().isEmpty()) {
                        String last = parts[parts.length - 1].trim();
                        String[] lastParts = last.split("\\s+");
                        if (lastParts.length > 1 && lastParts[lastParts.length - 1].matches("\\d+")) {
                            this.city = last.substring(0, last.lastIndexOf(lastParts[lastParts.length - 1])).trim();
                            if (postalCode == null || postalCode.trim().isEmpty()) {
                                this.postalCode = lastParts[lastParts.length - 1];
                            }
                        } else {
                            this.city = last;
                        }
                    }
                } else {
                    if (street == null || street.trim().isEmpty()) {
                        this.street = trimmed;
                    }
                    if (city == null || city.trim().isEmpty()) {
                        this.city = "Colombo";
                    }
                }
            }
        }

        // Fallbacks for MSSQL NOT NULL constraints
        if (street == null || street.trim().isEmpty()) {
            this.street = (address != null && !address.trim().isEmpty()) ? address.trim() : "Main Street";
        }
        if (city == null || city.trim().isEmpty()) {
            this.city = "Colombo";
        }
        if (postalCode == null || postalCode.trim().isEmpty()) {
            this.postalCode = "00100";
        }

        // Synchronize full address if empty
        if (address == null || address.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (street != null && !street.trim().isEmpty()) sb.append(street.trim());
            if (city != null && !city.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(city.trim());
            }
            if (postalCode != null && !postalCode.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(postalCode.trim());
            }
            this.address = sb.length() > 0 ? sb.toString() : "Main Street, Colombo 00100";
        }

        if (this.address == null || this.address.trim().length() < 10) {
            this.address = (this.address != null && !this.address.trim().isEmpty())
                    ? this.address.trim() + ", Colombo, Sri Lanka"
                    : "Main Street, Colombo 00100, Sri Lanka";
        }

        if (this.description == null || this.description.trim().length() < 10) {
            this.description = "Automated multi-level parking facility with 24/7 security and smart bay monitoring.";
        }

        if (managedByAdminId == null || managedByAdminId <= 0) {
            this.managedByAdminId = 12L;
        }
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public Integer getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(Integer totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public Integer getTotalFloors() {
        return totalFloors != null && totalFloors >= 1 ? totalFloors : 1;
    }

    public void setTotalFloors(Integer totalFloors) {
        this.totalFloors = totalFloors;
    }

    public java.util.List<Integer> getFloorNumbers() {
        int count = getTotalFloors();
        java.util.List<Integer> list = new java.util.ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(i);
        }
        return list;
    }

    public Integer getCurrentOccupancy() {
        return currentOccupancy;
    }

    public void setCurrentOccupancy(Integer currentOccupancy) {
        this.currentOccupancy = currentOccupancy;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    public void setStatus(FacilityStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == FacilityStatus.ACTIVE;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getAvailableCapacity() {
        int cap = totalCapacity != null ? totalCapacity : 0;
        int occ = currentOccupancy != null ? currentOccupancy : 0;
        return Math.max(0, cap - occ);
    }

    public Integer getOccupancyPercentage() {
        if (totalCapacity == null || totalCapacity <= 0) {
            return 0;
        }
        int occ = currentOccupancy != null ? currentOccupancy : 0;
        return (int) Math.min(100, Math.round(((double) occ / totalCapacity) * 100));
    }

    @Transient
    public boolean isOpenNow() {
        if (status != FacilityStatus.ACTIVE) {
            return false;
        }
        if (openingTime == null || closingTime == null) {
            return true;
        }
        LocalTime now = LocalDateTime.now().toLocalTime();
        if (openingTime.isBefore(closingTime) || openingTime.equals(closingTime)) {
            return !now.isBefore(openingTime) && !now.isAfter(closingTime);
        }
        return !now.isBefore(openingTime) || !now.isAfter(closingTime);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOperatingHoursFormatted() {
        String open = openingTime != null ? openingTime.toString() : "06:00";
        String close = closingTime != null ? closingTime.toString() : "23:00";
        return open + " - " + close;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public Long getManagedByAdminId() {
        return managedByAdminId;
    }

    public void setManagedByAdminId(Long managedByAdminId) {
        this.managedByAdminId = managedByAdminId;
    }

    @Transient
    public java.math.BigDecimal getBaseHourlyRate() {
        return java.math.BigDecimal.valueOf(250);
    }
}



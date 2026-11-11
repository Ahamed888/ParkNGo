package com.example.parkngo.vehicle;

import com.example.parkngo.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "Vehicle")
public class Vehicle {
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "CAR", "SEDAN", "SUV", "VAN", "MOTORCYCLE", "BIKE", "EV", "ELECTRIC", "TRUCK");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VehicleID")
    private Long id;

    @NotBlank(message = "License plate number is required")
    @Size(min = 4, max = 15, message = "Plate number must be between 4 and 15 characters")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(?:(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW|wp|cp|sp|nw|nc|ep|sg|va|uw)[ -]?)?([A-Za-z]{2,3})|(\\d{1,3}))[ -]?(\\d{4})$",
            message = "Please enter a valid Sri Lankan license plate number (e.g. WP CA-1234, CAB-5678, CP KA-9012, or 19-1234)"
    )
    @Column(name = "RegistrationNumber", nullable = false, unique = true, length = 50)
    private String registrationNumber;

    @NotBlank(message = "Vehicle type is required")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(CAR|SEDAN|SUV|VAN|MOTORCYCLE|BIKE|EV|ELECTRIC|TRUCK)$",
            message = "Please select a supported vehicle type")
    @Column(name = "VehicleType", nullable = false, length = 30)
    private String vehicleType = "SEDAN"; // SEDAN, SUV, TRUCK, ELECTRIC, MOTORCYCLE, VAN

    @NotBlank(message = "Brand / Make is required")
    @Size(min = 1, max = 50, message = "Brand must be between 1 and 50 characters")
    @Column(name = "Brand", nullable = false, length = 50)
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(min = 1, max = 50, message = "Model must be between 1 and 50 characters")
    @Column(name = "Model", nullable = false, length = 50)
    private String model;

    @Size(max = 30, message = "Color must be 30 characters or fewer")
    @Column(name = "Color", length = 30)
    private String color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID", nullable = true)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum VehicleStatus {
        ACTIVE, INACTIVE
    }

    public Vehicle() {}

    public Vehicle(Long id, String registrationNumber, String vehicleType, String brand, String model, String color, User customer, VehicleStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.registrationNumber = registrationNumber != null ? registrationNumber.trim().toUpperCase() : null;
        this.vehicleType = vehicleType;
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.customer = customer;
        this.status = status != null ? status : VehicleStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getLicensePlate() {
        return registrationNumber;
    }

    public void setLicensePlate(String licensePlate) {
        this.registrationNumber = licensePlate != null ? licensePlate.trim().toUpperCase() : null;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber != null ? registrationNumber.trim().toUpperCase() : null;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType != null ? vehicleType.trim().toUpperCase() : "SEDAN";
    }

    public static boolean isSupportedType(String vehicleType) {
        return vehicleType != null && SUPPORTED_TYPES.contains(vehicleType.trim().toUpperCase());
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand != null ? brand.trim() : null;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null ? model.trim() : null;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color != null && !color.isBlank() ? color.trim() : null;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Helper to get a human-readable vehicle type title
     */
    public String getVehicleTypeDisplay() {
        if (vehicleType == null) return "Car / Sedan";
        return switch (vehicleType.toUpperCase()) {
            case "SUV" -> "SUV / Crossover";
            case "ELECTRIC", "EV" -> "Electric Vehicle (EV)";
            case "TRUCK" -> "Pickup / Truck";
            case "MOTORCYCLE", "BIKE" -> "Motorcycle";
            case "VAN" -> "Van / Minivan";
            case "CAR", "SEDAN" -> "Sedan / Car";
            default -> vehicleType;
        };
    }

    /**
     * Helper to get FontAwesome icon class for vehicle type
     */
    public String getTypeIcon() {
        if (vehicleType == null) return "fa-solid fa-car";
        return switch (vehicleType.toUpperCase()) {
            case "SUV" -> "fa-solid fa-truck-monster";
            case "ELECTRIC", "EV" -> "fa-solid fa-bolt";
            case "TRUCK" -> "fa-solid fa-truck-pickup";
            case "MOTORCYCLE", "BIKE" -> "fa-solid fa-motorcycle";
            case "VAN" -> "fa-solid fa-van-shuttle";
            default -> "fa-solid fa-car";
        };
    }
}

package com.example.parkngo.reservation;

import com.example.parkngo.discount.Discount;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.user.User;
import com.example.parkngo.vehicle.Vehicle;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Reservation")
@Inheritance(strategy = InheritanceType.JOINED)
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReservationID")
    private Long id;

    @Column(name = "ReservationCode", unique = true, length = 30)
    private String reservationCode;

    @ManyToOne
    @JoinColumn(name = "CustomerID", nullable = true)
    private User customer;
    
    @ManyToOne
    @JoinColumn(name = "VerifiedByStaffID", nullable = true)
    private User issuedByStaff;

    @Column(name = "ContactEmail", length = 100)
    private String contactEmail;

    @Column(name = "ContactPhone", length = 30)
    private String contactPhone;

    @Column(name = "GuestName", length = 100)
    private String guestName;

    @ManyToOne
    // Existing SQL Server WALK_IN rows use guest vehicle fields and allow
    // VehicleID to be NULL; online reservations still validate a vehicle.
    @JoinColumn(name = "VehicleID", nullable = true)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "SpaceID", nullable = false)
    private ParkingSpace parkingSpace;

    @NotNull(message = "Arrival time is required")
    @Column(name = "ArrivalTime", nullable = false)
    private LocalDateTime arrivalTime;

    @NotNull(message = "Departure time is required")
    @Column(name = "DepartureTime", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "ActualArrivalTime")
    private LocalDateTime actualArrivalTime;

    @Column(name = "ActualDepartureTime")
    private LocalDateTime actualDepartureTime;

    @NotNull(message = "Reservation status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @NotNull(message = "Base amount cannot be null")
    @PositiveOrZero(message = "Base amount cannot be negative")
    @Column(name = "BaseAmount", nullable = false)
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @NotNull(message = "Service amount cannot be null")
    @PositiveOrZero(message = "Service amount cannot be negative")
    @Column(name = "ServiceAmount", nullable = false)
    private BigDecimal serviceAmount = BigDecimal.ZERO;

    @NotNull(message = "Discount amount cannot be null")
    @PositiveOrZero(message = "Discount amount cannot be negative")
    @Column(name = "DiscountAmount", nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull(message = "Total amount cannot be null")
    @PositiveOrZero(message = "Total amount cannot be negative")
    @Column(name = "TotalAmount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @NotNull(message = "Overstay charge cannot be null")
    @PositiveOrZero(message = "Overstay charge cannot be negative")
    @Column(name = "OverstayCharge", nullable = false)
    private BigDecimal overstayCharge = BigDecimal.ZERO;

    @Column(name = "DurationHours", insertable = false, updatable = false)
    private BigDecimal durationHours = BigDecimal.valueOf(1.0);

    @Column(name = "IsWalkIn")
    private Boolean isWalkIn = false;

    @Column(name = "ReservationType", length = 20)
    private String reservationType = "ONLINE";

    @ManyToOne
    @JoinColumn(name = "DiscountID")
    private Discount appliedDiscount;

    // Plain id rather than a JPA relation to Subscription, matching the
    // checkout module's own CheckoutQuote convention - set when this
    // reservation's price was covered (partly or fully) by spending one
    // visit of the customer's active membership pass at booking time.
    @Column(name = "AppliedSubscriptionID")
    private Long appliedSubscriptionId;

    @Column(name = "VerifiedAt")
    private LocalDateTime verifiedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationServiceItem> serviceItems = new ArrayList<>();

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "PaymentHoldExpiresAt")
    private LocalDateTime paymentHoldExpiresAt;

    public enum ReservationStatus {
        PENDING, CONFIRMED, VERIFIED, ACTIVE, OVERSTAYING, OVERSTAY_PAYMENT_DUE, COMPLETED, CANCELLED, EXPIRED
    }

    public Reservation() {}

    public Reservation(Long id, String reservationCode, User customer, Vehicle vehicle, ParkingSpace parkingSpace,
                       LocalDateTime arrivalTime, LocalDateTime departureTime, LocalDateTime actualArrivalTime,
                       LocalDateTime actualDepartureTime, ReservationStatus status, BigDecimal baseAmount,
                       BigDecimal serviceAmount, BigDecimal discountAmount, BigDecimal totalAmount,
                       BigDecimal overstayCharge, Boolean isWalkIn, Discount appliedDiscount,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.reservationCode = reservationCode;
        this.customer = customer;
        this.vehicle = vehicle;
        this.parkingSpace = parkingSpace;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.actualArrivalTime = actualArrivalTime;
        this.actualDepartureTime = actualDepartureTime;
        this.status = status;
        this.baseAmount = baseAmount;
        this.serviceAmount = serviceAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.overstayCharge = overstayCharge;
        this.isWalkIn = isWalkIn != null ? isWalkIn : false;
        this.appliedDiscount = appliedDiscount;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.isWalkIn == null) this.isWalkIn = false;
        if (this.baseAmount == null) this.baseAmount = BigDecimal.ZERO;
        if (this.serviceAmount == null) this.serviceAmount = BigDecimal.ZERO;
        if (this.discountAmount == null) this.discountAmount = BigDecimal.ZERO;
        if (this.totalAmount == null) this.totalAmount = BigDecimal.ZERO;
        if (this.overstayCharge == null) this.overstayCharge = BigDecimal.ZERO;
        if (this.status == null) this.status = ReservationStatus.CONFIRMED;
        if (Boolean.TRUE.equals(this.isWalkIn)) {
            this.reservationType = "WALK_IN";
        } else if (this.reservationType == null || this.reservationType.isBlank()) {
            this.reservationType = "ONLINE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReservationCode() {
        return reservationCode;
    }

    public void setReservationCode(String reservationCode) {
        this.reservationCode = reservationCode;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getIssuedByStaff() {
        return issuedByStaff;
    }

    public void setIssuedByStaff(User issuedByStaff) {
        this.issuedByStaff = issuedByStaff;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public ParkingSpace getParkingSpace() {
        return parkingSpace;
    }

    public void setParkingSpace(ParkingSpace parkingSpace) {
        this.parkingSpace = parkingSpace;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getActualArrivalTime() {
        return actualArrivalTime;
    }

    public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
    }

    public LocalDateTime getActualDepartureTime() {
        return actualDepartureTime;
    }

    public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
        this.actualDepartureTime = actualDepartureTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getServiceAmount() {
        return serviceAmount;
    }

    public void setServiceAmount(BigDecimal serviceAmount) {
        this.serviceAmount = serviceAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getOverstayCharge() {
        return overstayCharge;
    }

    public void setOverstayCharge(BigDecimal overstayCharge) {
        this.overstayCharge = overstayCharge;
    }

    public Boolean getIsWalkIn() {
        return isWalkIn;
    }

    public void setIsWalkIn(Boolean walkIn) {
        isWalkIn = walkIn;
    }

    public Discount getAppliedDiscount() {
        return appliedDiscount;
    }

    public void setAppliedDiscount(Discount appliedDiscount) {
        this.appliedDiscount = appliedDiscount;
    }

    public Long getAppliedSubscriptionId() {
        return appliedSubscriptionId;
    }

    public void setAppliedSubscriptionId(Long appliedSubscriptionId) {
        this.appliedSubscriptionId = appliedSubscriptionId;
    }

    public List<ReservationServiceItem> getServiceItems() {
        return serviceItems;
    }

    public void setServiceItems(List<ReservationServiceItem> serviceItems) {
        this.serviceItems = serviceItems;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPaymentHoldExpiresAt() {
        return paymentHoldExpiresAt;
    }

    public void setPaymentHoldExpiresAt(LocalDateTime paymentHoldExpiresAt) {
        this.paymentHoldExpiresAt = paymentHoldExpiresAt;
    }

    public BigDecimal getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(BigDecimal durationHours) {
        this.durationHours = durationHours;
    }

    public String getReservationType() {
        return reservationType;
    }

    public void setReservationType(String reservationType) {
        this.reservationType = reservationType;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}

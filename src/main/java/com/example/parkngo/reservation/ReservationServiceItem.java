package com.example.parkngo.reservation;

import com.example.parkngo.facility.FacilityService;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ReservationServiceItem")
public class ReservationServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ServiceItemID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ReservationID", nullable = false)
    private Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "ServiceID", nullable = false)
    private FacilityService service;

    @Column(name = "ServicePrice", nullable = false)
    private BigDecimal servicePrice = BigDecimal.ZERO;

    @Column(name = "Notes", length = 200)
    private String notes;

    public ReservationServiceItem() {}

    public ReservationServiceItem(Long id, Reservation reservation, FacilityService service, BigDecimal servicePrice) {
        this.id = id;
        this.reservation = reservation;
        this.service = service;
        this.servicePrice = servicePrice;
    }

    public ReservationServiceItem(Long id, Reservation reservation, FacilityService service, BigDecimal servicePrice, String notes) {
        this.id = id;
        this.reservation = reservation;
        this.service = service;
        this.servicePrice = servicePrice;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public FacilityService getService() {
        return service;
    }

    public void setService(FacilityService service) {
        this.service = service;
    }

    public BigDecimal getServicePrice() {
        return servicePrice;
    }

    public void setServicePrice(BigDecimal servicePrice) {
        this.servicePrice = servicePrice;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

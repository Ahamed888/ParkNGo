package com.example.parkngo.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "OnlineReservation")
@PrimaryKeyJoinColumn(name = "ReservationID")
public class OnlineReservation extends Reservation {

    @Column(name = "BookingSource", length = 50)
    private String bookingSource = "WEB_PORTAL";

    @Column(name = "AdvanceBookingRef", length = 60)
    private String advanceBookingRef;

    public OnlineReservation() {
        super();
        this.setIsWalkIn(false);
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource(String bookingSource) {
        this.bookingSource = bookingSource;
    }

    public String getAdvanceBookingRef() {
        return advanceBookingRef;
    }

    public void setAdvanceBookingRef(String advanceBookingRef) {
        this.advanceBookingRef = advanceBookingRef;
    }
}

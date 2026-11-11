package com.example.parkngo.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "WalkInReservation")
@PrimaryKeyJoinColumn(name = "ReservationID")
public class WalkInReservation extends Reservation {

    @Column(name = "GuestVehicleRegNumber", length = 30)
    private String guestVehicleRegNumber;

    @Column(name = "GuestContactNumber", length = 20)
    private String guestContactNumber;

    public WalkInReservation() {
        super();
        this.setIsWalkIn(true);
        this.setReservationType("WALK_IN");
    }

    public String getGuestVehicleRegNumber() {
        return guestVehicleRegNumber;
    }

    public void setGuestVehicleRegNumber(String guestVehicleRegNumber) {
        this.guestVehicleRegNumber = guestVehicleRegNumber;
    }

    public String getGuestContactNumber() {
        return guestContactNumber;
    }

    public void setGuestContactNumber(String guestContactNumber) {
        this.guestContactNumber = guestContactNumber;
    }
}

package com.example.parkngo.staff;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationService;
import com.example.parkngo.reservation.WalkInReservation;
import com.example.parkngo.facility.ParkingFacility;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class StaffPagesController {

    private final ReservationService reservationService;
    private final StaffFacilityAccessService staffFacilityAccess;

    public StaffPagesController(ReservationService reservationService, StaffFacilityAccessService staffFacilityAccess) {
        this.reservationService = reservationService;
        this.staffFacilityAccess = staffFacilityAccess;
    }

    @GetMapping("/staff/vehicles")
    public String staffVehicles(@RequestParam(required = false) String q, Model model) {
        ParkingFacility facility = staffFacilityAccess.currentAssignedFacility();
        List<Reservation> active = reservationService.getActiveReservations().stream()
                .filter(r -> belongsToFacility(r, facility.getId())).collect(Collectors.toList());
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase();
            active = active.stream()
                    .filter(r -> plateOf(r).toLowerCase().contains(term))
                    .collect(Collectors.toList());
        }
        model.addAttribute("activeReservations", active);
        model.addAttribute("filterQ", q);
        model.addAttribute("facility", facility);
        model.addAttribute("allFloors", true);
        return "staff/vehicles";
    }

    @GetMapping("/staff/reports")
    public String staffReports(Model model) {
        LocalDate today = LocalDate.now();
        ParkingFacility facility = staffFacilityAccess.currentAssignedFacility();
        List<Reservation> all = reservationService.getAllReservations().stream()
                .filter(r -> belongsToFacility(r, facility.getId())).toList();

        long arrivals = all.stream()
                .filter(r -> r.getActualArrivalTime() != null && r.getActualArrivalTime().toLocalDate().equals(today))
                .count();
        long departures = all.stream()
                .filter(r -> r.getActualDepartureTime() != null && r.getActualDepartureTime().toLocalDate().equals(today))
                .count();
        long walkIns = all.stream()
                .filter(r -> r.getActualArrivalTime() != null && r.getActualArrivalTime().toLocalDate().equals(today))
                .filter(r -> r instanceof WalkInReservation || Boolean.TRUE.equals(r.getIsWalkIn()))
                .count();

        double walkInRevenue = all.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .filter(r -> r.getActualDepartureTime() != null && r.getActualDepartureTime().toLocalDate().equals(today))
                .filter(r -> r instanceof WalkInReservation || Boolean.TRUE.equals(r.getIsWalkIn()))
                .mapToDouble(r -> r.getTotalAmount() != null ? r.getTotalAmount().doubleValue() : 0)
                .sum();

        model.addAttribute("reportDate", today);
        model.addAttribute("todayArrivals", arrivals);
        model.addAttribute("todayDepartures", departures);
        model.addAttribute("walkInsIssued", walkIns);
        model.addAttribute("walkInRevenue", walkInRevenue);
        model.addAttribute("activeCount", reservationService.getActiveReservations().stream()
                .filter(r -> belongsToFacility(r, facility.getId())).count());
        return "staff/reports";
    }

    private boolean belongsToFacility(Reservation reservation, Long facilityId) {
        return reservation.getParkingSpace() != null && reservation.getParkingSpace().getFacility() != null
                && facilityId.equals(reservation.getParkingSpace().getFacility().getId());
    }

    private String plateOf(Reservation reservation) {
        if (reservation.getVehicle() != null && reservation.getVehicle().getRegistrationNumber() != null) {
            return reservation.getVehicle().getRegistrationNumber();
        }
        return reservation instanceof WalkInReservation walkIn && walkIn.getGuestVehicleRegNumber() != null
                ? walkIn.getGuestVehicleRegNumber() : "";
    }
}

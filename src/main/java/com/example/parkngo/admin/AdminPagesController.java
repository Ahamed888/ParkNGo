package com.example.parkngo.admin;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Controller
public class AdminPagesController {

    private final ReservationService reservationService;
    private final ParkingFacilityService facilityService;

    public AdminPagesController(ReservationService reservationService, ParkingFacilityService facilityService) {
        this.reservationService = reservationService;
        this.facilityService = facilityService;
    }

    @GetMapping("/admin/reservations")
    public String adminReservations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String q,
            Model model) {
        List<Reservation> list = reservationService.getAllReservations();
        if (q != null && !q.isBlank()) {
            list = reservationService.searchReservations(q.trim());
        }
        if (status != null && !status.isBlank()) {
            try {
                Reservation.ReservationStatus st = Reservation.ReservationStatus.valueOf(status.toUpperCase());
                list = list.stream().filter(r -> r.getStatus() == st).collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (facilityId != null) {
            Long fid = facilityId;
            list = list.stream()
                    .filter(r -> r.getParkingSpace() != null
                            && r.getParkingSpace().getFacility() != null
                            && fid.equals(r.getParkingSpace().getFacility().getId()))
                    .collect(Collectors.toList());
        }
        if (date != null) {
            list = list.stream()
                    .filter(r -> r.getArrivalTime() != null && r.getArrivalTime().toLocalDate().equals(date))
                    .collect(Collectors.toList());
        }
        model.addAttribute("reservations", list);
        model.addAttribute("facilities", facilityService.getAllFacilities());
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterFacilityId", facilityId);
        model.addAttribute("filterDate", date);
        model.addAttribute("filterQ", q);
        return "admin/reservations";
    }

    @GetMapping({"/admin/reservations/suggest", "/admin/reservation/suggest"})
    @ResponseBody
    public List<Map<String, Object>> suggestReservations(@RequestParam(required = false, defaultValue = "") String q) {
        if (q == null || q.trim().isEmpty()) {
            return List.of();
        }
        List<Reservation> matches = reservationService.searchReservations(q.trim());
        return matches.stream()
                .limit(10)
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    String code = r.getReservationCode() != null ? r.getReservationCode() : "RES-" + r.getId();
                    String plate = r.getVehicle() != null ? r.getVehicle().getRegistrationNumber() : "";
                    String customer = r.getCustomer() != null ? r.getCustomer().getFullName() : "Guest";
                    String facility = r.getParkingSpace() != null && r.getParkingSpace().getFacility() != null
                            ? r.getParkingSpace().getFacility().getName() : "Unassigned";
                    String bay = r.getParkingSpace() != null ? "Bay " + r.getParkingSpace().getSpaceNumber() : "Bay pending";
                    String status = r.getStatus() != null ? r.getStatus().name() : "";

                    map.put("id", r.getId());
                    map.put("code", code);
                    map.put("plate", plate);
                    map.put("customer", customer);
                    map.put("facility", facility);
                    map.put("bay", bay);
                    map.put("status", status);
                    return map;
                })
                .toList();
    }
}

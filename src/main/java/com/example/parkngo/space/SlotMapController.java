package com.example.parkngo.space;

import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.util.SecurityUtil;
import com.example.parkngo.staff.StaffFacilityAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Objects;

@RestController
public class SlotMapController {
    private final SlotMapService service;
    private final ReservationRepository reservations;
    private final SecurityUtil security;
    private final StaffFacilityAccessService staffFacilityAccess;
    public SlotMapController(SlotMapService service, ReservationRepository reservations, SecurityUtil security,
                             StaffFacilityAccessService staffFacilityAccess) {
        this.service = service; this.reservations = reservations; this.security = security;
        this.staffFacilityAccess = staffFacilityAccess;
    }

    public ResponseEntity<SlotMapService.Snapshot> map(Long facilityId, LocalDateTime arrivalTime,
            LocalDateTime departureTime, Long excludeReservationId) {
        return map(facilityId, arrivalTime, departureTime, excludeReservationId, null);
    }

    @GetMapping({"/customer/reservation/slot-map", "/staff/slot-map", "/admin/slot-map"})
    public ResponseEntity<SlotMapService.Snapshot> map(@RequestParam Long facilityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @RequestParam(required = false) Long excludeReservationId,
            HttpServletRequest request) {
        String path = request == null ? "/admin/slot-map" : request.getRequestURI();
        SlotMapService.BookingType bookingType = path.startsWith("/customer/")
                ? SlotMapService.BookingType.ONLINE
                : path.startsWith("/staff/") ? SlotMapService.BookingType.WALK_IN : SlotMapService.BookingType.ADMIN;
        if (security.hasRole("STAFF")) {
            staffFacilityAccess.requireFacility(facilityId);
        }
        if (excludeReservationId != null) {
            var reservation = reservations.findById(excludeReservationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            Long userId = security.getCurrentUserId();
            if (userId == null || reservation.getCustomer() == null || !Objects.equals(userId, reservation.getCustomer().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }
        LocalDateTime arrival = arrivalTime == null ? LocalDateTime.now() : arrivalTime;
        LocalDateTime departure = departureTime == null ? arrival.plusHours(1) : departureTime;
        if (!departure.isAfter(arrival)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time window");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.snapshot(facilityId, arrival, departure, excludeReservationId, bookingType));
    }

    @GetMapping("/staff/walk-in/slot-map")
    public ResponseEntity<SlotMapService.Snapshot> walkInMap(@RequestParam Long facilityId,
            @RequestParam(defaultValue = "2") Integer durationHours) {
        staffFacilityAccess.requireFacility(facilityId);
        if (durationHours == null || durationHours < 1 || durationHours > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid walk-in duration");
        }
        LocalDateTime arrival = LocalDateTime.now();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.snapshot(facilityId, arrival, arrival.plusHours(durationHours), null,
                        SlotMapService.BookingType.WALK_IN));
    }
}

package com.example.parkngo.space;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.reservation.ReservationValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Public bay information only: never return occupants' identities or plates. */
@Service
@Transactional(readOnly = true)
public class SlotMapService {
    private final ParkingSpaceRepository spaces;
    private final ReservationRepository reservations;
    public SlotMapService(ParkingSpaceRepository spaces, ReservationRepository reservations) {
        this.spaces = spaces;
        this.reservations = reservations;
    }
    public record Bay(Long id, String spaceNumber, Integer floor, String zone, String status,
                      boolean occupied, boolean selectable, LocalDateTime estimatedDeparture,
                      BigDecimal hourlyRate, boolean hasEVCharging, boolean isAccessible,
                      String parkingType, String allocation) {}
    public record Snapshot(LocalDateTime updatedAt, List<Bay> bays) {}
    public enum BookingType { ONLINE, WALK_IN, ADMIN }

    public Snapshot snapshot(Long facilityId, LocalDateTime arrival, LocalDateTime departure, Long excludeId) {
        return snapshot(facilityId, arrival, departure, excludeId, BookingType.ADMIN);
    }

    public Snapshot snapshot(Long facilityId, LocalDateTime arrival, LocalDateTime departure, Long excludeId,
                             BookingType bookingType) {
        LocalDateTime now = LocalDateTime.now();
        if (arrival == null || departure == null || !departure.isAfter(arrival)) {
            throw new IllegalArgumentException("Choose a departure after your arrival.");
        }
        var bySpace = reservations.findLiveForFacility(facilityId).stream()
                .collect(Collectors.groupingBy(r -> r.getParkingSpace().getId()));
        var bays = spaces.findByFacilityId(facilityId).stream()
                .sorted(Comparator.comparing(ParkingSpace::getSpaceNumber, Comparator.nullsLast(String::compareTo)))
                .map(s -> describe(s, bySpace.getOrDefault(s.getId(), List.of()), arrival, departure, excludeId, now,
                        bookingType))
                .toList();
        return new Snapshot(now, bays);
    }

    static Bay describe(ParkingSpace s, List<Reservation> bookings, LocalDateTime arrival,
                        LocalDateTime departure, Long excludeId, LocalDateTime now) {
        return describe(s, bookings, arrival, departure, excludeId, now, BookingType.ADMIN);
    }

    static Bay describe(ParkingSpace s, List<Reservation> bookings, LocalDateTime arrival,
                        LocalDateTime departure, Long excludeId, LocalDateTime now, BookingType bookingType) {
        var active = bookings.stream().filter(r -> r.getStatus() == Reservation.ReservationStatus.ACTIVE).toList();
        boolean occupied = s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED || !active.isEmpty();
        LocalDateTime estimate = active.stream().map(Reservation::getDepartureTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        int buffer = ReservationValidator.TURNOVER_BUFFER_MINUTES;
        boolean conflict = bookings.stream().filter(r -> excludeId == null || !excludeId.equals(r.getId()))
                .anyMatch(r -> r.getArrivalTime().isBefore(departure.plusMinutes(buffer))
                        && r.getDepartureTime().isAfter(arrival.minusMinutes(buffer)));
        // An overdue/untracked car cannot be assumed gone. A future booking can use a known later release.
        boolean physicallyBlocked = occupied && (estimate == null || !estimate.isAfter(now)
                || !arrival.isAfter(estimate.plusMinutes(buffer)));
        boolean maintenance = s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE;
        boolean outOfService = s.getStatus() == ParkingSpace.ParkingSpaceStatus.OUT_OF_SERVICE;
        boolean unexplainedHold = s.getStatus() == ParkingSpace.ParkingSpaceStatus.RESERVED && bookings.isEmpty();
        boolean allocationAllowed = bookingType == BookingType.ADMIN || supports(s.getAllocationType(), bookingType);
        boolean selectable = allocationAllowed && !maintenance && !outOfService && !conflict
                && !physicallyBlocked && !unexplainedHold;
        String status = maintenance ? "MAINTENANCE" : outOfService ? "OUT_OF_SERVICE"
                : !allocationAllowed ? (bookingType == BookingType.WALK_IN ? "ONLINE_ONLY" : "WALK_IN_ONLY")
                : occupied ? "OCCUPIED" : conflict || unexplainedHold ? "RESERVED" : "AVAILABLE";
        return new Bay(s.getId(), s.getSpaceNumber(), s.getFloor(), s.getZone(), status, occupied, selectable,
                estimate, s.getHourlyRate(), Boolean.TRUE.equals(s.getHasEVCharging()), Boolean.TRUE.equals(s.getIsAccessible()),
                s.getParkingType(), s.getAllocationType());
    }

    private static boolean supports(String allocation, BookingType bookingType) {
        if (allocation == null || allocation.isBlank() || "GENERAL".equalsIgnoreCase(allocation)
                || "BOTH".equalsIgnoreCase(allocation)) {
            return true;
        }
        return bookingType == BookingType.ONLINE
                ? !"WALKIN_ONLY".equalsIgnoreCase(allocation)
                : !"ONLINE_BOOKABLE".equalsIgnoreCase(allocation);
    }
}

package com.example.parkngo.dashboard;

import com.example.parkngo.dashboard.dto.*;
import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityService;
import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationService;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ReservationService reservationService;
    private final ParkingSpaceService spaceService;
    private final ParkingFacilityService facilityService;

    public DashboardService(ReservationService reservationService,
                            ParkingSpaceService spaceService,
                            ParkingFacilityService facilityService) {
        this.reservationService = reservationService;
        this.spaceService = spaceService;
        this.facilityService = facilityService;
    }

    public void enrichStaffDashboard(ModelLike model, Long facilityId, Integer floor) {
        List<ParkingFacility> facilities = facilityService.getAllFacilities();

        ParkingFacility facility = null;
        if (facilityId != null) {
            try {
                facility = facilityService.getFacilityById(facilityId);
            } catch (Exception ignored) {
            }
        }
        if (facility == null && !facilities.isEmpty()) {
            facility = facilities.get(0);
        }
        if (facility == null) {
            return;
        }
        final Long scopedFacilityId = facility.getId();
        // Staff never receive a network-wide facility list.  The selected
        // facility has already been authorized by the caller.
        model.put("facilities", List.of(facility));

        List<ParkingSpace> allSpaces = spaceService.getFacilityParkingSpaces(facility.getId());
        List<Integer> floors = allSpaces.stream()
                .map(ParkingSpace::getFloor)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        int selectedFloor = floor != null ? floor : (floors.isEmpty() ? 1 : floors.get(0));
        model.put("floors", floors);
        model.put("selectedFloor", selectedFloor);
        model.put("facility", facility);

        List<ParkingSpace> levelSpaces = allSpaces.stream()
                .filter(s -> Objects.equals(s.getFloor(), selectedFloor))
                .toList();
        model.put("levelSpaces", levelSpaces);

        long total = allSpaces.size();
        long available = allSpaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE).count();
        model.put("totalSpaces", total);
        model.put("availableSpaces", available);
        model.put("occupiedSpaces", total - available);
        model.put("occupancyPercentage", total > 0 ? (int) ((total - available) * 100 / total) : 0);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        List<Reservation> all = reservationService.getAllReservations().stream()
                .filter(r -> r.getParkingSpace() != null && r.getParkingSpace().getFacility() != null
                        && Objects.equals(r.getParkingSpace().getFacility().getId(), scopedFacilityId))
                .toList();

        long todayArrivals = countArrivalsOn(all, today);
        long yesterdayArrivals = countArrivalsOn(all, yesterday);
        long todayDepartures = countDeparturesOn(all, today);
        long yesterdayDepartures = countDeparturesOn(all, yesterday);

        model.put("todayArrivals", todayArrivals);
        model.put("todayDepartures", todayDepartures);
        model.put("arrivalsDeltaPct", deltaPct(todayArrivals, yesterdayArrivals));
        model.put("departuresDeltaPct", deltaPct(todayDepartures, yesterdayDepartures));
        model.put("activeVehicles", reservationService.getActiveReservations().stream()
                .filter(r -> r.getParkingSpace() != null && r.getParkingSpace().getFacility() != null
                        && Objects.equals(r.getParkingSpace().getFacility().getId(), scopedFacilityId))
                .count());

        List<Reservation> recentArrivals = all.stream()
                .filter(r -> r.getActualArrivalTime() != null)
                .sorted(Comparator.comparing(Reservation::getActualArrivalTime).reversed())
                .limit(5)
                .toList();
        model.put("recentArrivals", recentArrivals);
    }

    public void enrichAdminDashboard(Map<String, Object> model, LocalDate date, Long facilityId) {
        LocalDate selectedDate = date != null ? date : LocalDate.now();
        model.put("selectedDate", selectedDate);
        model.put("selectedFacilityId", facilityId);

        List<ParkingFacility> facilities = facilityService.getAllFacilities();
        model.put("facilityCount", facilities.size());

        List<ParkingSpace> scopedSpaces = new ArrayList<>();
        for (ParkingFacility f : facilities) {
            if (facilityId == null || Objects.equals(f.getId(), facilityId)) {
                scopedSpaces.addAll(spaceService.getFacilityParkingSpaces(f.getId()));
            }
        }

        long total = scopedSpaces.size();
        long occupied = scopedSpaces.stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)
                .count();
        long available = scopedSpaces.stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.AVAILABLE)
                .count();

        model.put("totalSpaces", total);
        model.put("occupiedSpaces", occupied);
        model.put("availableSpaces", available);
        model.put("occupancyPercentage", total > 0 ? (int) (occupied * 100 / total) : 0);

        model.put("hourlyOccupancy", buildHourlyOccupancy(scopedSpaces));
        model.put("hourlyRevenue", buildHourlyRevenue(selectedDate));
        model.put("revenueToday", revenueForDate(selectedDate));
        model.put("revenueDeltaPct", deltaPct(
                revenueForDate(selectedDate).doubleValue(),
                revenueForDate(selectedDate.minusDays(1)).doubleValue()));
        model.put("topComplexes", topComplexes(facilities));
    }

    public void enrichFacilityDetails(Map<String, Object> model, ParkingFacility facility, List<ParkingSpace> spaces,
                                      List<com.example.parkngo.facility.FacilityService> facilityServices) {
        enrichFacilityDetails(model::put, facility, spaces, facilityServices);
    }

    public void enrichFacilityDetails(ModelLike model, ParkingFacility facility, List<ParkingSpace> spaces,
                                      List<com.example.parkngo.facility.FacilityService> facilityServices) {
        model.put("facilityServices", facilityServices);

        Map<Integer, List<ParkingSpace>> byFloor = spaces.stream()
                .filter(s -> s.getFloor() != null)
                .collect(Collectors.groupingBy(ParkingSpace::getFloor));

        List<FloorSummary> floorSummaries = byFloor.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    long occ = e.getValue().stream()
                            .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)
                            .count();
                    int pct = e.getValue().isEmpty() ? 0 : (int) (occ * 100 / e.getValue().size());
                    return new FloorSummary(e.getKey(), e.getValue().size(), pct);
                })
                .toList();
        model.put("floorSummaries", floorSummaries);

        List<FacilityActivityRow> recentActivity = reservationService.getAllReservations().stream()
                .filter(r -> r.getParkingSpace() != null
                        && r.getParkingSpace().getFacility() != null
                        && Objects.equals(r.getParkingSpace().getFacility().getId(), facility.getId()))
                .sorted(Comparator.comparing(this::activityTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toActivity)
                .toList();
        model.put("recentActivity", recentActivity);

        Map<String, List<BigDecimal>> ratesByType = spaces.stream()
                .filter(s -> s.getParkingType() != null && s.getHourlyRate() != null)
                .collect(Collectors.groupingBy(
                        ParkingSpace::getParkingType,
                        Collectors.mapping(ParkingSpace::getHourlyRate, Collectors.toList())));
        model.put("ratesByType", ratesByType);

        List<ParkingSpace> maintenance = spaces.stream()
                .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.MAINTENANCE)
                .toList();
        model.put("maintenanceSpaces", maintenance);
    }

    public void enrichStaffParkingSpaces(Map<String, Object> model, List<ParkingSpace> spaces, int selectedFloor) {
        List<Integer> floors = spaces.stream()
                .map(ParkingSpace::getFloor)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        model.put("floors", floors);
        model.put("selectedFloorNo", selectedFloor);

        List<ParkingSpace> level = spaces.stream()
                .filter(s -> Objects.equals(s.getFloor(), selectedFloor))
                .toList();

        long total = level.size();
        long occupied = level.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
        model.put("levelTotals", Map.of("total", total, "occupied", occupied, "available", total - occupied));

        Map<String, List<ParkingSpace>> byZone = level.stream()
                .collect(Collectors.groupingBy(s -> s.getZone() != null ? s.getZone() : "DEFAULT"));
        List<ZoneStat> zoneStats = byZone.entrySet().stream()
                .map(e -> {
                    long occ = e.getValue().stream()
                            .filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED)
                            .count();
                    int pct = e.getValue().isEmpty() ? 0 : (int) (occ * 100 / e.getValue().size());
                    return new ZoneStat(e.getKey(), e.getValue().size(), occ, pct);
                })
                .sorted(Comparator.comparing(ZoneStat::zone))
                .toList();
        model.put("zoneStats", zoneStats);
    }

    private LocalDateTime activityTime(Reservation r) {
        if (r.getActualDepartureTime() != null) return r.getActualDepartureTime();
        if (r.getActualArrivalTime() != null) return r.getActualArrivalTime();
        return r.getArrivalTime();
    }

    private FacilityActivityRow toActivity(Reservation r) {
        String plate = r.getVehicle() != null ? r.getVehicle().getRegistrationNumber() : "&mdash;";
        String action = r.getActualDepartureTime() != null ? "Check-out" : "Check-in";
        LocalDateTime t = activityTime(r);
        ParkingSpace sp = r.getParkingSpace();
        return new FacilityActivityRow(t, plate, action,
                sp != null ? sp.getSpaceNumber() : "&mdash;",
                sp != null ? sp.getFloor() : null);
    }

    private long countArrivalsOn(List<Reservation> all, LocalDate day) {
        return all.stream()
                .filter(r -> r.getActualArrivalTime() != null && r.getActualArrivalTime().toLocalDate().equals(day))
                .count();
    }

    private long countDeparturesOn(List<Reservation> all, LocalDate day) {
        return all.stream()
                .filter(r -> r.getActualDepartureTime() != null && r.getActualDepartureTime().toLocalDate().equals(day))
                .count();
    }

    private Integer deltaPct(long today, long yesterday) {
        if (yesterday == 0) return null;
        return (int) Math.round(((today - yesterday) * 100.0) / yesterday);
    }

    private Integer deltaPct(double today, double yesterday) {
        if (yesterday == 0) return null;
        return (int) Math.round(((today - yesterday) * 100.0) / yesterday);
    }

    private BigDecimal revenueForDate(LocalDate date) {
        return reservationService.getAllReservations().stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .filter(r -> r.getActualDepartureTime() != null && r.getActualDepartureTime().toLocalDate().equals(date))
                .map(Reservation::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<HourlyRevenuePoint> buildHourlyRevenue(LocalDate date) {
        List<HourlyRevenuePoint> points = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int hour = h;
            BigDecimal sum = reservationService.getAllReservations().stream()
                    .filter(r -> r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                    .filter(r -> r.getActualDepartureTime() != null && r.getActualDepartureTime().toLocalDate().equals(date))
                    .filter(r -> r.getActualDepartureTime().getHour() == hour)
                    .map(Reservation::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new HourlyRevenuePoint(h, sum));
        }
        return points;
    }

    private List<HourlyOccupancyPoint> buildHourlyOccupancy(List<ParkingSpace> spaces) {
        int[] hours = {6, 9, 12, 15, 18, 21};
        String[] labels = {"6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM"};
        int total = spaces.size();
        int occ = (int) spaces.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
        int occPct = total > 0 ? (int) (occ * 100 / total) : 0;
        int availPct = 100 - occPct;
        List<HourlyOccupancyPoint> list = new ArrayList<>();
        for (int i = 0; i < hours.length; i++) {
            list.add(new HourlyOccupancyPoint(labels[i], occPct, availPct));
        }
        return list;
    }

    private List<ComplexUtilizationRow> topComplexes(List<ParkingFacility> facilities) {
        return facilities.stream()
                .map(f -> {
                    List<ParkingSpace> sp = spaceService.getFacilityParkingSpaces(f.getId());
                    long occ = sp.stream().filter(s -> s.getStatus() == ParkingSpace.ParkingSpaceStatus.OCCUPIED).count();
                    int pct = sp.isEmpty() ? 0 : (int) (occ * 100 / sp.size());
                    return new ComplexUtilizationRow(f.getName(), pct);
                })
                .sorted(Comparator.comparingInt(ComplexUtilizationRow::utilizationPct).reversed())
                .limit(5)
                .toList();
    }

    /** Minimal map-like adapter for Spring Model without coupling. */
    public interface ModelLike {
        void put(String key, Object value);
    }
}

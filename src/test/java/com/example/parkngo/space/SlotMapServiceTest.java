package com.example.parkngo.space;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlotMapServiceTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 25, 10, 0);
    private ParkingSpace space() {
        var s = new ParkingSpace(); s.setId(1L); s.setSpaceNumber("A01");
        s.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);return s;
    }
    private Reservation booking(Reservation.ReservationStatus status, LocalDateTime end) {
        var r = new Reservation();r.setId(7L);r.setStatus(status);r.setArrivalTime(now.minusHours(1));r.setDepartureTime(end);return r;
    }
    @Test void occupiedBayShowsCarAndScheduledRelease() {
        var bay = SlotMapService.describe(space(), List.of(booking(Reservation.ReservationStatus.ACTIVE,now.plusHours(1))),now,now.plusHours(2),null,now);
        assertTrue(bay.occupied());assertFalse(bay.selectable());assertEquals(now.plusHours(1),bay.estimatedDeparture());
    }
    @Test void overdueCarDoesNotBecomeAvailableEvenForLaterWindow() {
        var bay = SlotMapService.describe(space(), List.of(booking(Reservation.ReservationStatus.ACTIVE,now.minusMinutes(5))),now.plusHours(1),now.plusHours(2),null,now);
        assertFalse(bay.selectable());assertTrue(bay.occupied());
    }
    @Test void untrackedOccupancyDoesNotInventReleaseEstimate() {
        var s=space();s.setStatus(ParkingSpace.ParkingSpaceStatus.OCCUPIED);
        var bay=SlotMapService.describe(s,List.of(),now,now.plusHours(1),null,now);
        assertFalse(bay.selectable());assertNull(bay.estimatedDeparture());assertTrue(bay.occupied());
    }
    @Test void laterBookingCanUseBayAfterKnownDepartureAndTurnover() {
        var bay=SlotMapService.describe(space(),List.of(booking(Reservation.ReservationStatus.ACTIVE,now.plusMinutes(30))),now.plusHours(1),now.plusHours(2),null,now);
        assertTrue(bay.selectable());assertTrue(bay.occupied());
    }
    @Test void editExcludesOnlyItsOwnBooking() {
        var r=booking(Reservation.ReservationStatus.CONFIRMED,now.plusHours(1));
        assertFalse(SlotMapService.describe(space(),List.of(r),now,now.plusHours(2),null,now).selectable());
        assertTrue(SlotMapService.describe(space(),List.of(r),now,now.plusHours(2),7L,now).selectable());
        var other=booking(Reservation.ReservationStatus.CONFIRMED,now.plusHours(1));other.setId(8L);
        assertFalse(SlotMapService.describe(space(),List.of(r,other),now,now.plusHours(2),7L,now).selectable());
    }
    @Test void turnoverAndOperationallyUnavailableBaysAreRespected() {
        var r=booking(Reservation.ReservationStatus.CONFIRMED,now.minusMinutes(10));
        assertFalse(SlotMapService.describe(space(),List.of(r),now,now.plusHours(2),null,now).selectable());
        var s=space();s.setStatus(ParkingSpace.ParkingSpaceStatus.MAINTENANCE);
        assertFalse(SlotMapService.describe(s,List.of(),now,now.plusHours(2),null,now).selectable());
        s.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);s.setAllocationType("LONG_TERM");
        assertTrue(SlotMapService.describe(s,List.of(),now,now.plusHours(2),null,now).selectable());
    }
    @Test void snapshotKeepsAllDatabaseFloorsAndSpacesDynamic() {
        ParkingSpaceRepository spaces = mock(ParkingSpaceRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        ParkingSpace levelOne = space(); levelOne.setFloor(1); levelOne.setSpaceNumber("A01");
        ParkingSpace levelEight = space(); levelEight.setId(2L); levelEight.setFloor(8); levelEight.setSpaceNumber("H47");
        when(spaces.findByFacilityId(10L)).thenReturn(List.of(levelEight, levelOne));
        when(reservations.findLiveForFacility(10L)).thenReturn(List.of());

        var snapshot = new SlotMapService(spaces, reservations).snapshot(10L, now, now.plusHours(2), null);

        assertEquals(2, snapshot.bays().size());
        assertEquals(List.of(1, 8), snapshot.bays().stream().map(SlotMapService.Bay::floor).toList());
        assertEquals(List.of("A01", "H47"), snapshot.bays().stream().map(SlotMapService.Bay::spaceNumber).toList());
    }
}

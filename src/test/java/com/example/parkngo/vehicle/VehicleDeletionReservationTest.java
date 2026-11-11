package com.example.parkngo.vehicle;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleDeletionReservationTest {
    @Mock VehicleRepository vehicleRepository;
    @Mock ReservationRepository reservationRepository;

    private VehicleService vehicleService;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, reservationRepository);
        vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setStatus(Vehicle.VehicleStatus.ACTIVE);
    }

    @Test
    void customerCannotDeleteVehicleWithConfirmedReservation() {
        Reservation confirmed = new Reservation();
        confirmed.setStatus(Reservation.ReservationStatus.CONFIRMED);
        when(vehicleRepository.findByIdAndCustomerId(12L, 5L)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.findActiveOrUpcomingReservationsByVehicleId(12L))
                .thenReturn(List.of(confirmed));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> vehicleService.deleteVehicle(12L, 5L));

        assertEquals("This vehicle cannot be removed while it has a pending, confirmed, or active reservation. Cancel or complete the reservation first.",
                exception.getMessage());
        verify(vehicleRepository, never()).save(vehicle);
        verify(vehicleRepository, never()).delete(vehicle);
    }

    @Test
    void vehicleWithOnlyHistoricalReservationsCanBeSoftRemoved() throws Exception {
        when(vehicleRepository.findByIdAndCustomerId(12L, 5L)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.findActiveOrUpcomingReservationsByVehicleId(12L)).thenReturn(List.of());
        when(reservationRepository.countByVehicleId(12L)).thenReturn(1L);

        vehicleService.deleteVehicle(12L, 5L);

        assertEquals(Vehicle.VehicleStatus.INACTIVE, vehicle.getStatus());
        verify(vehicleRepository).save(vehicle);
        verify(vehicleRepository, never()).delete(vehicle);
    }

    @Test
    void internalDeletePathAlsoBlocksLiveReservations() {
        Reservation active = new Reservation();
        active.setStatus(Reservation.ReservationStatus.ACTIVE);
        when(vehicleRepository.findById(12L)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.findActiveOrUpcomingReservationsByVehicleId(12L))
                .thenReturn(List.of(active));

        assertThrows(IllegalStateException.class, () -> vehicleService.deleteVehicle(12L));

        verify(vehicleRepository, never()).save(vehicle);
        verify(vehicleRepository, never()).delete(vehicle);
    }
}

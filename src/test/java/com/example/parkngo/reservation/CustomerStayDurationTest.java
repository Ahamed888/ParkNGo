package com.example.parkngo.reservation;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityServiceRepository;

class CustomerStayDurationTest {
    private final ReservationValidator validator = new ReservationValidator(
        mock(ReservationRepository.class), mock(DiscountService.class), mock(FacilityServiceRepository.class));

    private ReservationValidationException validate(long seconds) {
        Reservation reservation = new Reservation();
        reservation.setArrivalTime(LocalDateTime.now().plusDays(1).withNano(0));
        reservation.setDepartureTime(reservation.getArrivalTime().plusSeconds(seconds));
        // Missing vehicle and space cause other errors; inspect the duration error specifically.
        return assertThrows(ReservationValidationException.class,
            () -> validator.validateForBooking(reservation, null, null));
    }

    @Test
    void acceptsEveryWholeHourFromOneToEight() {
        for (int hours = 1; hours <= 8; hours++) {
            assertFalse(validate(hours * 3600L).getFieldErrors().containsKey("departureTime"));
        }
    }

    @Test
    void rejectsFractionalAndOutOfRangeDurations() {
        for (long seconds : new long[]{0, 1800, 5400, 28801, 32400, 3601}) {
            assertEquals("Choose a stay of 1 to 8 whole hours.",
                validate(seconds).getFieldErrors().get("departureTime"));
        }
    }
}

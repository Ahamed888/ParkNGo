package com.example.parkngo.validation;

import com.example.parkngo.discount.DiscountService;
import com.example.parkngo.facility.FacilityServiceRepository;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.reservation.ReservationValidationException;
import com.example.parkngo.reservation.ReservationValidator;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.vehicle.Vehicle;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InputConstraintValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsForgedVehicleTypeButKeepsEstablishedAliases() {
        Vehicle vehicle = validVehicle();
        vehicle.setVehicleType("SPACESHIP");
        assertTrue(validator.validateProperty(vehicle, "vehicleType").stream()
                .anyMatch(v -> v.getMessage().contains("supported vehicle type")));

        for (String supported : Vehicle.SUPPORTED_TYPES) {
            vehicle.setVehicleType(supported);
            assertTrue(validator.validateProperty(vehicle, "vehicleType").isEmpty(), supported);
        }
    }

    @Test
    void walkInRejectsForgedVehicleType() {
        ReservationValidator reservationValidator = new ReservationValidator(
                mock(ReservationRepository.class), mock(DiscountService.class), mock(FacilityServiceRepository.class));
        ParkingSpace space = validSpace();

        ReservationValidationException error = assertThrows(ReservationValidationException.class,
                () -> reservationValidator.validateForWalkIn(space, "WP CA-1234", "SPACESHIP", 2, null, null, null));

        assertTrue(error.getFieldErrors().containsKey("vehicleType"));
    }

    @Test
    void parkingSpaceRejectsForgedTypesAndInvalidCodeOrRate() {
        ParkingSpace space = validSpace();

        space.setParkingType("FLYING");
        assertFalse(validator.validateProperty(space, "parkingType").isEmpty());
        space.setParkingType("STANDARD");

        space.setAllocationType("PRIVATE_UNKNOWN");
        assertFalse(validator.validateProperty(space, "allocationType").isEmpty());
        space.setAllocationType("GENERAL");

        space.setSpaceNumber("A@1");
        assertFalse(validator.validateProperty(space, "spaceNumber").isEmpty());
        space.setSpaceNumber("A-01");

        space.setHourlyRate(new BigDecimal("10.999"));
        assertFalse(validator.validateProperty(space, "hourlyRate").isEmpty());
        space.setHourlyRate(new BigDecimal("1000000.00"));
        assertFalse(validator.validateProperty(space, "hourlyRate").isEmpty());
    }

    private Vehicle validVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1234");
        vehicle.setVehicleType("SEDAN");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        return vehicle;
    }

    private ParkingSpace validSpace() {
        ParkingSpace space = new ParkingSpace();
        space.setId(1L);
        space.setSpaceNumber("A-01");
        space.setFloor(1);
        space.setZone("ZONE-A");
        space.setParkingType("STANDARD");
        space.setAllocationType("GENERAL");
        space.setHourlyRate(new BigDecimal("250.00"));
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        return space;
    }
}

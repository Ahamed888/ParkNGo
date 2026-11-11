package com.example.parkngo.facility;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// [VIVA - Validation Testing]: Verifies Bean Validation constraints (@NotBlank, @Min, @AssertTrue) independently of database.
class ParkingFacilityValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private ParkingFacility createValidFacility() {
        ParkingFacility facility = new ParkingFacility();
        facility.setName("Colombo City Garage");
        facility.setAddress("42 Galle Road, Colombo 03");
        facility.setContactNumber("+94771234567");
        facility.setDescription("Convenient multi-level city parking facility.");
        facility.setOpeningTime(LocalTime.of(6, 0));
        facility.setClosingTime(LocalTime.of(22, 0));
        facility.setTotalCapacity(150);
        facility.setTotalFloors(4);
        facility.setCurrentOccupancy(10);
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        return facility;
    }

    @Test
    @DisplayName("Valid facility has zero constraint violations")
    void testValidFacility() {
        ParkingFacility facility = createValidFacility();
        Set<ConstraintViolation<ParkingFacility>> violations = validator.validate(facility);
        assertTrue(violations.isEmpty(), "Expected 0 violations for valid facility");
    }

    @Test
    @DisplayName("Phone validation accepts valid contact numbers starting with +94")
    void testValidPhoneNumbers() {
        ParkingFacility facility = createValidFacility();

        facility.setContactNumber("+94771234567");
        assertTrue(validator.validate(facility).isEmpty());

        facility.setContactNumber("+94112345678");
        assertTrue(validator.validate(facility).isEmpty());

        facility.setContactNumber("+94-11-2345678");
        assertTrue(validator.validate(facility).isEmpty());

        facility.setContactNumber("+94 77 123 4567");
        assertTrue(validator.validate(facility).isEmpty());

        facility.setContactNumber("+94-77-1234567");
        assertTrue(validator.validate(facility).isEmpty());
    }

    @Test
    @DisplayName("Phone validation rejects contact numbers missing +94 or invalid format")
    void testInvalidPhoneNumbers() {
        ParkingFacility facility = createValidFacility();

        facility.setContactNumber("0771234567"); // missing +94 mandatory prefix
        Set<ConstraintViolation<ParkingFacility>> v1 = validator.validate(facility);
        assertFalse(v1.isEmpty());

        facility.setContactNumber("011-2345678"); // missing +94 mandatory prefix
        Set<ConstraintViolation<ParkingFacility>> v2 = validator.validate(facility);
        assertFalse(v2.isEmpty());

        facility.setContactNumber("123"); // too short (< 7)
        Set<ConstraintViolation<ParkingFacility>> v3 = validator.validate(facility);
        assertFalse(v3.isEmpty());

        facility.setContactNumber("phone-number-invalid"); // contains alpha text
        Set<ConstraintViolation<ParkingFacility>> v4 = validator.validate(facility);
        assertFalse(v4.isEmpty());
    }

    @Test
    @DisplayName("Total capacity bounds validation (1 to 5000)")
    void testTotalCapacityBounds() {
        ParkingFacility facility = createValidFacility();

        facility.setTotalCapacity(0);
        Set<ConstraintViolation<ParkingFacility>> v1 = validator.validate(facility);
        assertFalse(v1.isEmpty());

        facility.setTotalCapacity(5001);
        Set<ConstraintViolation<ParkingFacility>> v2 = validator.validate(facility);
        assertFalse(v2.isEmpty());

        facility.setTotalCapacity(5000);
        assertTrue(validator.validate(facility).isEmpty());

        facility.setTotalCapacity(1);
        assertTrue(validator.validate(facility).isEmpty());
    }

    @Test
    @DisplayName("Cross-field validation: closing time must not be before opening time")
    void testOpeningClosingTimeValidation() {
        ParkingFacility facility = createValidFacility();

        facility.setOpeningTime(LocalTime.of(20, 0));
        facility.setClosingTime(LocalTime.of(8, 0)); // closing before opening

        Set<ConstraintViolation<ParkingFacility>> violations = validator.validate(facility);
        assertFalse(violations.isEmpty());
        boolean hasTimeError = violations.stream()
                .anyMatch(v -> v.getMessage().contains("Closing time must be after opening time"));
        assertTrue(hasTimeError, "Expected cross-field time error message");
    }

    @Test
    @DisplayName("Facility name length constraints (2 to 100 characters)")
    void testFacilityNameConstraints() {
        ParkingFacility facility = createValidFacility();

        facility.setName("A"); // too short (< 2)
        assertFalse(validator.validate(facility).isEmpty());

        facility.setName("AB"); // valid (>= 2)
        assertTrue(validator.validate(facility).isEmpty());

        facility.setName("A".repeat(101)); // too long (> 100)
        assertFalse(validator.validate(facility).isEmpty());
    }

    @Test
    @DisplayName("Address length constraints (at least 10 characters)")
    void testFacilityAddressConstraints() {
        ParkingFacility facility = createValidFacility();

        facility.setAddress("Short"); // < 10 characters
        assertFalse(validator.validate(facility).isEmpty());

        facility.setAddress("123 Main St"); // 11 characters (>= 10)
        assertTrue(validator.validate(facility).isEmpty());

        facility.setAddress("A".repeat(256)); // > 255 characters
        assertFalse(validator.validate(facility).isEmpty());
    }

    @Test
    @DisplayName("Description constraints (at least 10 characters)")
    void testFacilityDescriptionConstraints() {
        ParkingFacility facility = createValidFacility();

        facility.setDescription(""); // empty
        assertFalse(validator.validate(facility).isEmpty());

        facility.setDescription("Too short"); // 9 chars (< 10)
        assertFalse(validator.validate(facility).isEmpty());

        facility.setDescription("Valid description with 10+ characters.");
        assertTrue(validator.validate(facility).isEmpty());
    }

    @Test
    @DisplayName("City and postal code respect their persisted length limits")
    void testCityAndPostalCodeLengthConstraints() {
        ParkingFacility facility = createValidFacility();
        facility.setCity("C".repeat(81));
        assertTrue(validator.validateProperty(facility, "city").stream()
                .anyMatch(v -> v.getMessage().contains("80 characters")));

        facility.setCity("Colombo");
        facility.setPostalCode("1".repeat(21));
        assertTrue(validator.validateProperty(facility, "postalCode").stream()
                .anyMatch(v -> v.getMessage().contains("20 characters")));
    }

    @Test
    @DisplayName("FacilityService validations: price non-negative, required fields")
    void testFacilityServiceValidation() {
        FacilityService service = new FacilityService();
        service.setName("Car Wash");
        service.setDescription("Full wash");
        service.setPrice(new BigDecimal("15.50"));
        service.setStatus(FacilityService.ServiceStatus.AVAILABLE);

        assertTrue(validator.validate(service).isEmpty());

        // Negative price test
        service.setPrice(new BigDecimal("-1.00"));
        assertFalse(validator.validate(service).isEmpty());

        // Price with too many fractional digits
        service.setPrice(new BigDecimal("15.999"));
        assertFalse(validator.validate(service).isEmpty());

        // Blank name test
        service.setPrice(new BigDecimal("10.00"));
        service.setName("");
        assertFalse(validator.validate(service).isEmpty());
    }

    @Test
    @DisplayName("Total floors validation: 1 to 10 allowed, null, negative or out of range rejected")
    void testTotalFloorsValidation() {
        ParkingFacility facility = createValidFacility();

        facility.setTotalFloors(1);
        assertTrue(validator.validate(facility).isEmpty(), "1 floor should be valid (min boundary)");

        facility.setTotalFloors(4);
        assertTrue(validator.validate(facility).isEmpty(), "4 floors should be valid");

        facility.setTotalFloors(10);
        assertTrue(validator.validate(facility).isEmpty(), "10 floors should be valid (max boundary)");

        facility.setTotalFloors(null);
        assertFalse(validator.validate(facility).isEmpty(), "null floors should fail");

        facility.setTotalFloors(0);
        assertFalse(validator.validate(facility).isEmpty(), "0 floors should fail");

        facility.setTotalFloors(-1);
        assertFalse(validator.validate(facility).isEmpty(), "Negative floors should fail");

        facility.setTotalFloors(11);
        assertFalse(validator.validate(facility).isEmpty(), "11 floors should fail (cannot exceed 10)");
    }
}

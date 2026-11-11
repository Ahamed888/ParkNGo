package com.example.parkngo.facility;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.FacilityStaffAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// [VIVA - Unit Testing (JUnit 5 & Mockito)]: Tests service layer business logic in isolation with mocked repository dependencies.
@ExtendWith(MockitoExtension.class)
class ParkingFacilityServiceTest {

    @Mock
    private ParkingFacilityRepository facilityRepository;

    @Mock
    private ParkingSpaceRepository parkingSpaceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private FacilityStaffAccountService facilityStaffAccountService;

    @InjectMocks
    private ParkingFacilityService facilityService;

    private ParkingFacility testFacility;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facilityService, "facilityStaffAccountService", facilityStaffAccountService);
        testFacility = new ParkingFacility();
        testFacility.setId(1L);
        testFacility.setName("Downtown Garage");
        testFacility.setAddress("123 Main St");
        testFacility.setContactNumber("+94771234567");
        testFacility.setOpeningTime(LocalTime.of(6, 0));
        testFacility.setClosingTime(LocalTime.of(23, 0));
        testFacility.setTotalCapacity(10);
        testFacility.setCurrentOccupancy(0);
        testFacility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        testFacility.setDescription("5-level facility");
    }

    @Test
    @DisplayName("Create facility successfully")
    void testCreateFacilitySuccess() throws Exception {
        when(facilityRepository.findByNameIgnoreCase("Downtown Garage")).thenReturn(Optional.empty());
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        ParkingFacility created = facilityService.createFacility(testFacility);

        assertNotNull(created);
        assertEquals("Downtown Garage", created.getName());
        assertEquals(ParkingFacility.FacilityStatus.ACTIVE, created.getStatus());
        verify(facilityRepository).save(testFacility);
        verify(facilityStaffAccountService).ensureStaffAccount(testFacility);
    }

    @Test
    @DisplayName("Create facility fails when duplicate name exists (case-insensitive)")
    void testCreateFacilityDuplicateNameCaseInsensitive() {
        when(facilityRepository.findByNameIgnoreCase("downtown garage")).thenReturn(Optional.of(testFacility));

        ParkingFacility duplicate = new ParkingFacility();
        duplicate.setName("  downtown garage  ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(duplicate));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(facilityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get facility by ID successfully")
    void testGetFacilityByIdSuccess() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));

        ParkingFacility found = facilityService.getFacilityById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
        assertEquals("Downtown Garage", found.getName());
    }

    @Test
    @DisplayName("Get facility by ID throws exception when not found")
    void testGetFacilityByIdNotFound() {
        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> facilityService.getFacilityById(999L));
    }

    @Test
    @DisplayName("Get all facilities returns only ACTIVE facilities")
    void testGetAllFacilities() {
        when(facilityRepository.findByStatus(ParkingFacility.FacilityStatus.ACTIVE)).thenReturn(Arrays.asList(testFacility));

        List<ParkingFacility> list = facilityService.getAllFacilities();

        assertEquals(1, list.size());
        assertEquals("Downtown Garage", list.get(0).getName());
        verify(facilityRepository).findByStatus(ParkingFacility.FacilityStatus.ACTIVE);
    }

    @Test
    @DisplayName("Get all facilities including inactive returns all facilities")
    void testGetAllFacilitiesIncludingInactive() {
        ParkingFacility inactiveFacility = new ParkingFacility();
        inactiveFacility.setId(2L);
        inactiveFacility.setName("Closed Lot");
        inactiveFacility.setStatus(ParkingFacility.FacilityStatus.INACTIVE);

        when(facilityRepository.findAll()).thenReturn(Arrays.asList(testFacility, inactiveFacility));

        List<ParkingFacility> list = facilityService.getAllFacilitiesIncludingInactive();

        assertEquals(2, list.size());
        verify(facilityRepository).findAll();
    }

    @Test
    @DisplayName("Update facility successfully")
    void testUpdateFacilitySuccess() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.findByNameIgnoreCaseAndIdNot("Updated Garage", 1L)).thenReturn(Optional.empty());
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        ParkingFacility updateData = new ParkingFacility();
        updateData.setName("Updated Garage");
        updateData.setAddress("456 Broad St");
        updateData.setContactNumber("+94771234567");
        updateData.setOpeningTime(LocalTime.of(7, 0));
        updateData.setClosingTime(LocalTime.of(22, 0));
        updateData.setTotalCapacity(15);
        updateData.setDescription("Upgraded garage");

        ParkingFacility updated = facilityService.updateFacility(1L, updateData);

        assertNotNull(updated);
        assertEquals("Updated Garage", testFacility.getName());
        assertEquals(15, testFacility.getTotalCapacity());
        verify(facilityRepository).save(testFacility);
    }

    @Test
    @DisplayName("Update facility throws IllegalArgumentException when new capacity < current occupancy")
    void testUpdateFacilityCapacityLessThanOccupancy() throws Exception {
        testFacility.setCurrentOccupancy(20);
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.findByNameIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(Optional.empty());

        ParkingFacility updateData = new ParkingFacility();
        updateData.setName("Updated Garage");
        updateData.setAddress("456 Broad St");
        updateData.setContactNumber("+94771234567");
        updateData.setOpeningTime(LocalTime.of(7, 0));
        updateData.setClosingTime(LocalTime.of(22, 0));
        updateData.setTotalCapacity(10); // less than 20

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> facilityService.updateFacility(1L, updateData));
        assertTrue(exception.getMessage().contains("cannot be less than current occupancy"));
        verify(facilityRepository, never()).save(testFacility);
    }

    @Test
    @DisplayName("Deactivate facility successfully when no customer slot bookings exist")
    void testDeactivateFacilitySuccess() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of());
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.OCCUPIED)).thenReturn(0L);
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.RESERVED)).thenReturn(0L);
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        facilityService.deactivateFacility(1L);

        assertEquals(ParkingFacility.FacilityStatus.INACTIVE, testFacility.getStatus());
        verify(facilityRepository).save(testFacility);
    }

    @Test
    @DisplayName("Deactivate facility blocked when active customer reservations exist")
    void testDeactivateFacilityBlockedByActiveReservations() throws Exception {
        ParkingSpace space = new ParkingSpace();
        space.setId(10L);
        space.setFacility(testFacility);

        Reservation reservation = new Reservation();
        reservation.setParkingSpace(space);
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of(reservation));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> facilityService.deactivateFacility(1L));
        assertTrue(exception.getMessage().contains("customer slot booking"));
        verify(facilityRepository, never()).save(testFacility);
    }

    @Test
    @DisplayName("Deactivate facility blocked when bays are actively occupied")
    void testDeactivateFacilityBlockedByOccupiedBays() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of());
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.OCCUPIED)).thenReturn(3L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> facilityService.deactivateFacility(1L));
        assertTrue(exception.getMessage().contains("customer slot booking"));
        verify(facilityRepository, never()).save(testFacility);
    }

    @Test
    @DisplayName("Deactivate facility blocked when bays are actively reserved")
    void testDeactivateFacilityBlockedByReservedBays() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of());
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.OCCUPIED)).thenReturn(0L);
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.RESERVED)).thenReturn(2L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> facilityService.deactivateFacility(1L));
        assertTrue(exception.getMessage().contains("customer slot booking"));
        verify(facilityRepository, never()).save(testFacility);
    }

    @Test
    @DisplayName("Reactivate facility successfully")
    void testReactivateFacilitySuccess() throws Exception {
        testFacility.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        facilityService.reactivateFacility(1L);

        assertEquals(ParkingFacility.FacilityStatus.ACTIVE, testFacility.getStatus());
        verify(facilityRepository).save(testFacility);
    }

    @Test
    @DisplayName("Toggle facility status from ACTIVE to INACTIVE and INACTIVE to ACTIVE")
    void testToggleFacilityStatus() throws Exception {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of());
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.OCCUPIED)).thenReturn(0L);
        when(parkingSpaceRepository.countByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.RESERVED)).thenReturn(0L);
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        facilityService.toggleFacilityStatus(1L);
        assertEquals(ParkingFacility.FacilityStatus.INACTIVE, testFacility.getStatus());

        facilityService.toggleFacilityStatus(1L);
        assertEquals(ParkingFacility.FacilityStatus.ACTIVE, testFacility.getStatus());
    }

    @Test
    @DisplayName("Hard delete facility successfully when no parking bays are attached")
    void testHardDeleteFacilitySuccess() throws Exception {
        when(facilityRepository.existsById(1L)).thenReturn(true);
        when(parkingSpaceRepository.countByFacilityId(1L)).thenReturn(0L);

        facilityService.hardDeleteFacility(1L);

        verify(facilityRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Hard delete facility blocked when parking bays exist")
    void testHardDeleteFacilityBlockedWhenSpacesExist() throws Exception {
        when(facilityRepository.existsById(1L)).thenReturn(true);
        when(parkingSpaceRepository.countByFacilityId(1L)).thenReturn(10L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> facilityService.hardDeleteFacility(1L));
        assertTrue(exception.getMessage().contains("parking spaces"));
        verify(facilityRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Update occupancy clamps to 0 and total capacity")
    void testUpdateOccupancy() throws Exception {
        testFacility.setTotalCapacity(100);
        testFacility.setCurrentOccupancy(20);
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        facilityService.updateOccupancy(1L, 10);
        assertEquals(30, testFacility.getCurrentOccupancy());

        facilityService.updateOccupancy(1L, -50);
        assertEquals(0, testFacility.getCurrentOccupancy());

        facilityService.updateOccupancy(1L, 200);
        assertEquals(100, testFacility.getCurrentOccupancy());
    }

    @Test
    @DisplayName("Entity helper methods compute correct availability, percentages, and status")
    void testEntityHelperMethods() {
        testFacility.setTotalCapacity(100);
        testFacility.setCurrentOccupancy(20);
        assertEquals(80, testFacility.getAvailableCapacity());
        assertEquals(20, testFacility.getOccupancyPercentage());
        assertEquals("06:00 - 23:00", testFacility.getOperatingHoursFormatted());
        assertTrue(testFacility.isActive());
        assertEquals(List.of(1), testFacility.getFloorNumbers());
    }

    @Test
    @DisplayName("Update facility rejects reducing totalFloors below existing space floors")
    void testUpdateFacilityRejectReducingFloorsBelowExistingSpaces() {
        testFacility.setTotalFloors(4);
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.findByNameIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(Optional.empty());
        when(parkingSpaceRepository.findMaxFloorByFacilityId(1L)).thenReturn(4);

        ParkingFacility updateRequest = new ParkingFacility();
        updateRequest.setName("Downtown Garage");
        updateRequest.setTotalFloors(2); // Reduced from 4 to 2 while spaces on Floor 4 exist

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> facilityService.updateFacility(1L, updateRequest));
        assertTrue(ex.getMessage().contains("Cannot reduce floor count to 2"));
        assertTrue(ex.getMessage().contains("exist on Floor 4"));
    }

    @Test
    @DisplayName("Update facility succeeds when totalFloors is valid and above existing spaces")
    void testUpdateFacilitySucceedsWithValidFloors() throws Exception {
        testFacility.setTotalFloors(3);
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(facilityRepository.findByNameIgnoreCaseAndIdNot(anyString(), eq(1L))).thenReturn(Optional.empty());
        when(parkingSpaceRepository.findMaxFloorByFacilityId(1L)).thenReturn(2);
        when(facilityRepository.save(any(ParkingFacility.class))).thenAnswer(i -> i.getArgument(0));

        ParkingFacility updateRequest = new ParkingFacility();
        updateRequest.setName("Downtown Garage");
        updateRequest.setTotalFloors(5);

        ParkingFacility result = facilityService.updateFacility(1L, updateRequest);
        assertEquals(5, result.getTotalFloors());
        assertEquals(List.of(1, 2, 3, 4, 5), result.getFloorNumbers());
    }

    @Test
    @DisplayName("Create facility validates name length, address, contact format, capacity, and hours")
    void testCreateFacilityValidationFailures() {
        // Name too short (< 3)
        ParkingFacility shortName = new ParkingFacility(null, "AB", "123 Main St", "0771234567",
                LocalTime.of(6, 0), LocalTime.of(22, 0), 100, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(shortName));

        // Blank address
        ParkingFacility blankAddress = new ParkingFacility(null, "Valid Garage", "   ", "0771234567",
                LocalTime.of(6, 0), LocalTime.of(22, 0), 100, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(blankAddress));

        // Invalid phone number
        ParkingFacility invalidPhone = new ParkingFacility(null, "Valid Garage", "123 Main St", "12345",
                LocalTime.of(6, 0), LocalTime.of(22, 0), 100, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(invalidPhone));

        // Total capacity < 1
        ParkingFacility zeroCap = new ParkingFacility(null, "Valid Garage", "123 Main St", "0771234567",
                LocalTime.of(6, 0), LocalTime.of(22, 0), 0, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(zeroCap));

        // Total capacity > 5000
        ParkingFacility excessiveCap = new ParkingFacility(null, "Valid Garage", "123 Main St", "0771234567",
                LocalTime.of(6, 0), LocalTime.of(22, 0), 5001, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(excessiveCap));

        // Closing time not after opening time
        ParkingFacility badHours = new ParkingFacility(null, "Valid Garage", "123 Main St", "0771234567",
                LocalTime.of(22, 0), LocalTime.of(6, 0), 100, 0, null);
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(badHours));
    }

    @Test
    @DisplayName("Deactivate facility blocked by upcoming reservation with future departure")
    void testDeactivateFacilityBlockedByFutureReservations() throws Exception {
        ParkingSpace space = new ParkingSpace();
        space.setId(10L);
        space.setFacility(testFacility);

        Reservation futureRes = new Reservation();
        futureRes.setParkingSpace(space);
        futureRes.setStatus(Reservation.ReservationStatus.CONFIRMED);
        futureRes.setDepartureTime(java.time.LocalDateTime.now().plusDays(2));

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));
        when(reservationRepository.findAll()).thenReturn(List.of(futureRes));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> facilityService.deactivateFacility(1L));
        assertTrue(exception.getMessage().contains("Cannot inactivate facility"));
        verify(facilityRepository, never()).save(testFacility);
    }

    @Test
    @DisplayName("Allocate optimal space using Strategy context")
    void testAllocateOptimalSpaceUsingStrategy() {
        ParkingSpace s1 = new ParkingSpace();
        s1.setId(101L);
        s1.setSpaceNumber("F1-L1A-01");
        s1.setFloor(1);
        s1.setZone("Zone A");
        s1.setHasEVCharging(true);

        when(parkingSpaceRepository.findByFacilityIdAndStatus(1L, ParkingSpace.ParkingSpaceStatus.AVAILABLE))
                .thenReturn(List.of(s1));

        com.example.parkngo.pattern.strategy.SpaceAllocationContext mockContext =
                mock(com.example.parkngo.pattern.strategy.SpaceAllocationContext.class);
        when(mockContext.allocateSpace(anyList(), any())).thenReturn(Optional.of(s1));

        facilityService.setSpaceAllocationContext(mockContext);

        Optional<ParkingSpace> result = facilityService.allocateOptimalSpace(
                1L, com.example.parkngo.pattern.strategy.AllocationCriteria.ev());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-01", result.get().getSpaceNumber());
        verify(mockContext).allocateSpace(anyList(), any());
    }

    @Test
    @DisplayName("Allocate optimal space returns empty when facility ID is null")
    void testAllocateOptimalSpaceNullFacility() {
        Optional<ParkingSpace> result = facilityService.allocateOptimalSpace(
                null, com.example.parkngo.pattern.strategy.AllocationCriteria.standard());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Create and update facility accepts Sri Lankan number with +94 (e.g., +94-11-2345678)")
    void testFacilityAcceptsLandlineWithHyphen() throws Exception {
        testFacility.setContactNumber("+94-11-2345678");
        when(facilityRepository.findByNameIgnoreCase("Downtown Garage")).thenReturn(Optional.empty());
        when(facilityRepository.save(any(ParkingFacility.class))).thenReturn(testFacility);

        ParkingFacility created = facilityService.createFacility(testFacility);
        assertNotNull(created);
        assertEquals("+94-11-2345678", created.getContactNumber());

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(testFacility));

        ParkingFacility updateData = new ParkingFacility();
        updateData.setContactNumber("+94-11-2345678");
        ParkingFacility updated = facilityService.updateFacility(1L, updateData);
        assertNotNull(updated);
        assertEquals("+94-11-2345678", updated.getContactNumber());
    }

    @Test
    @DisplayName("Create facility rejects invalid phone numbers or missing +94")
    void testCreateFacilityRejectsInvalidPhone() {
        testFacility.setContactNumber("123");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(testFacility));
        assertTrue(ex.getMessage().contains("+94"));

        testFacility.setContactNumber("0771234567"); // missing +94
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(testFacility));

        testFacility.setContactNumber("phone-number-invalid");
        assertThrows(IllegalArgumentException.class, () -> facilityService.createFacility(testFacility));
    }
}

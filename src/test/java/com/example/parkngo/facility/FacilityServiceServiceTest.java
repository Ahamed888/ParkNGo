package com.example.parkngo.facility;

import com.example.parkngo.reservation.ReservationServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacilityServiceServiceTest {

    @Mock
    private FacilityServiceRepository serviceRepository;

    @Mock
    private ReservationServiceRepository reservationServiceRepository;

    @Mock
    private ParkingFacilityRepository facilityRepository;

    @InjectMocks
    private FacilityServiceService serviceService;

    private ParkingFacility facility;
    private FacilityService testService;

    @BeforeEach
    void setUp() {
        facility = new ParkingFacility();
        facility.setId(1L);
        facility.setName("Downtown Complex");
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);

        testService = new FacilityService();
        testService.setId(10L);
        testService.setName("Hand Car Wash");
        testService.setDescription("Full foam wash");
        testService.setPrice(BigDecimal.valueOf(15.00));
        testService.setFacility(facility);
        testService.setStatus(FacilityService.ServiceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Create service successfully when name is unique in facility")
    void testCreateServiceSuccess() throws Exception {
        when(serviceRepository.findByFacilityIdAndNameIgnoreCase(1L, "Hand Car Wash")).thenReturn(Optional.empty());
        when(serviceRepository.save(any(FacilityService.class))).thenReturn(testService);

        FacilityService created = serviceService.createService(testService);

        assertNotNull(created);
        assertEquals("Hand Car Wash", created.getName());
        verify(serviceRepository).save(testService);
    }

    @Test
    @DisplayName("Create service fails when duplicate name exists in same facility (case-insensitive)")
    void testCreateServiceDuplicateNameInSameFacility() {
        when(serviceRepository.findByFacilityIdAndNameIgnoreCase(1L, "hand car wash")).thenReturn(Optional.of(testService));

        FacilityService duplicate = new FacilityService();
        duplicate.setName("  hand car wash  ");
        duplicate.setFacility(facility);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> serviceService.createService(duplicate));
        assertTrue(exception.getMessage().contains("already exists for this facility"));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get service by ID successfully")
    void testGetServiceByIdSuccess() throws Exception {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));

        FacilityService found = serviceService.getServiceById(10L);

        assertNotNull(found);
        assertEquals(10L, found.getId());
        assertEquals("Hand Car Wash", found.getName());
    }

    @Test
    @DisplayName("Get service by ID throws exception when not found")
    void testGetServiceByIdNotFound() {
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> serviceService.getServiceById(999L));
    }

    @Test
    @DisplayName("Get all services for a given facility")
    void testGetFacilityServices() {
        when(serviceRepository.findByFacilityId(1L)).thenReturn(Arrays.asList(testService));

        List<FacilityService> list = serviceService.getFacilityServices(1L);

        assertEquals(1, list.size());
        assertEquals("Hand Car Wash", list.get(0).getName());
    }

    @Test
    @DisplayName("Get available services only for customer booking")
    void testGetAvailableServices() {
        when(serviceRepository.findByFacilityIdAndStatus(1L, FacilityService.ServiceStatus.AVAILABLE))
                .thenReturn(Arrays.asList(testService));

        List<FacilityService> list = serviceService.getAvailableServices(1L);

        assertEquals(1, list.size());
        assertTrue(list.get(0).isAvailable());
    }

    @Test
    @DisplayName("Update service data and host facility")
    void testUpdateServiceSuccess() throws Exception {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(serviceRepository.findByFacilityIdAndNameIgnoreCaseAndIdNot(1L, "Premium Detailing", 10L)).thenReturn(Optional.empty());
        when(serviceRepository.save(any(FacilityService.class))).thenReturn(testService);

        FacilityService updateData = new FacilityService();
        updateData.setName("Premium Detailing");
        updateData.setDescription("Complete detailing");
        updateData.setPrice(BigDecimal.valueOf(30.00));
        updateData.setStatus(FacilityService.ServiceStatus.AVAILABLE);

        FacilityService updated = serviceService.updateService(10L, updateData, facility);

        assertNotNull(updated);
        assertEquals("Premium Detailing", testService.getName());
        assertEquals(BigDecimal.valueOf(30.00), testService.getPrice());
        verify(serviceRepository).save(testService);
    }

    @Test
    @DisplayName("Delete removes service from catalog without breaking historical references")
    void testHardDeleteService() throws Exception {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));

        serviceService.hardDeleteService(10L);

        assertTrue(testService.isDeleted());
        assertFalse(testService.isAvailable());
        verify(serviceRepository).saveAndFlush(testService);
        verify(serviceRepository, never()).deleteById(any());
        verifyNoInteractions(reservationServiceRepository);
    }

    @Test
    void removedServicesAreHiddenAndCannotBeReactivated() {
        testService.setDeleted(true);
        when(serviceRepository.findAll()).thenReturn(List.of(testService));
        when(serviceRepository.findByFacilityId(1L)).thenReturn(List.of(testService));
        when(serviceRepository.findByFacilityIdAndStatus(1L, FacilityService.ServiceStatus.AVAILABLE)).thenReturn(List.of(testService));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        assertTrue(serviceService.getAllServices().isEmpty());
        assertTrue(serviceService.getFacilityServices(1L).isEmpty());
        assertTrue(serviceService.getAvailableServices(1L).isEmpty());
        assertThrows(Exception.class, () -> serviceService.activateService(10L));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Entity helper methods return appropriate FontAwesome icons")
    void testEntityHelperMethods() {
        assertTrue(testService.isAvailable());
        assertEquals("fa-soap", testService.getIconClass());

        FacilityService ev = new FacilityService();
        ev.setName("EV Supercharging");
        assertEquals("fa-bolt", ev.getIconClass());

        FacilityService valet = new FacilityService();
        valet.setName("Valet Parking");
        assertEquals("fa-user-tie", valet.getIconClass());

        FacilityService detailing = new FacilityService();
        detailing.setName("Interior Detailing");
        assertEquals("fa-wand-magic-sparkles", detailing.getIconClass());
    }

    @Test
    @DisplayName("Create assigns one service to multiple active facilities")
    void createAssignsMultipleActiveFacilities() throws Exception {
        ParkingFacility second = activeFacility(2L, "City Centre Garage");
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility, second));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FacilityService created = serviceService.createService(testService, List.of(1L, 2L));

        assertEquals(Set.of(1L, 2L), facilityIds(created));
        verify(serviceRepository).save(testService);
    }

    @Test
    @DisplayName("Edit adds multiple facilities in one update")
    void editAddsMultipleFacilities() throws Exception {
        ParkingFacility second = activeFacility(2L, "City Centre Garage");
        ParkingFacility third = activeFacility(3L, "Airport Parking");
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility, second, third));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceService.updateService(10L, updateData(), List.of(1L, 2L, 3L));

        assertEquals(Set.of(1L, 2L, 3L), facilityIds(testService));
    }

    @Test
    @DisplayName("Edit removes multiple facilities in one update")
    void editRemovesMultipleFacilities() throws Exception {
        testService.setFacilities(List.of(facility,
                activeFacility(2L, "City Centre Garage"),
                activeFacility(3L, "Airport Parking")));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceService.updateService(10L, updateData(), List.of(1L));

        assertEquals(Set.of(1L), facilityIds(testService));
    }

    @Test
    @DisplayName("Edit adds and removes facilities in the same update")
    void editAddsAndRemovesInSameUpdate() throws Exception {
        ParkingFacility removed = activeFacility(2L, "Old Garage");
        ParkingFacility addedOne = activeFacility(3L, "Airport Parking");
        ParkingFacility addedTwo = activeFacility(4L, "Negombo Complex");
        testService.setFacilities(List.of(facility, removed));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility, addedOne, addedTwo));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceService.updateService(10L, updateData(), List.of(1L, 3L, 4L));

        assertEquals(Set.of(1L, 3L, 4L), facilityIds(testService));
    }

    @Test
    @DisplayName("Inactive facility cannot be newly assigned")
    void inactiveFacilityCannotBeNewlyAssigned() {
        ParkingFacility inactive = activeFacility(2L, "Closed Garage");
        inactive.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        when(facilityRepository.findAllById(any())).thenReturn(List.of(inactive));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> serviceService.createService(testService, List.of(2L)));

        assertTrue(error.getMessage().contains("cannot be newly assigned"));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Invalid facility ID is rejected")
    void invalidFacilityIdIsRejected() {
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> serviceService.createService(testService, List.of(1L, 999L)));

        assertTrue(error.getMessage().contains("do not exist"));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate facility IDs create only one relationship")
    void duplicateFacilityIdsAreDeduplicated() throws Exception {
        ParkingFacility second = activeFacility(2L, "City Centre Garage");
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility, second));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FacilityService created = serviceService.createService(testService, List.of(1L, 1L, 2L, 2L));

        assertEquals(2, created.getFacilities().size());
        assertEquals(Set.of(1L, 2L), facilityIds(created));
    }

    @Test
    @DisplayName("Existing inactive assignment is preserved during edit")
    void existingInactiveAssignmentIsPreserved() throws Exception {
        ParkingFacility inactive = activeFacility(2L, "Temporarily Closed Garage");
        inactive.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        testService.setFacilities(List.of(facility, inactive));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(facilityRepository.findAllById(any())).thenReturn(List.of(facility));
        when(serviceRepository.save(any(FacilityService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        serviceService.updateService(10L, updateData(), List.of(1L));

        assertEquals(Set.of(1L, 2L), facilityIds(testService));
    }

    @Test
    @DisplayName("belongsToFacility and getFacilityIdsCsv correctly reflect multi-facility assignments")
    void testBelongsToFacilityAndCsv() {
        ParkingFacility fac2 = activeFacility(2L, "Kandy Garage");
        testService.setFacilities(List.of(facility, fac2));

        assertTrue(testService.belongsToFacility(1L));
        assertTrue(testService.belongsToFacility(2L));
        assertFalse(testService.belongsToFacility(99L));
        assertFalse(testService.belongsToFacility(null));

        String csv = testService.getFacilityIdsCsv();
        assertTrue(csv.contains("1"));
        assertTrue(csv.contains("2"));
    }

    private ParkingFacility activeFacility(Long id, String name) {
        ParkingFacility result = new ParkingFacility();
        result.setId(id);
        result.setName(name);
        result.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
        return result;
    }

    private FacilityService updateData() {
        FacilityService result = new FacilityService();
        result.setName(testService.getName());
        result.setDescription(testService.getDescription());
        result.setPrice(testService.getPrice());
        result.setStatus(testService.getStatus());
        return result;
    }

    private Set<Long> facilityIds(FacilityService service) {
        return service.getFacilities().stream().map(ParkingFacility::getId).collect(java.util.stream.Collectors.toSet());
    }
}

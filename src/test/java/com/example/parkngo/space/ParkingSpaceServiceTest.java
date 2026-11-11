package com.example.parkngo.space;

import com.example.parkngo.facility.ParkingFacility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingSpaceServiceTest {

    @Mock
    private ParkingSpaceRepository parkingSpaceRepository;

    @InjectMocks
    private ParkingSpaceService spaceService;

    private ParkingFacility facility;

    @BeforeEach
    void setUp() {
        facility = new ParkingFacility();
        facility.setId(10L);
        facility.setName("Central Mall Garage");
        facility.setTotalFloors(3); // 3 floors: 1, 2, 3
    }

    @Test
    @DisplayName("Create space with valid floor within facility totalFloors succeeds")
    void testCreateSpaceValidFloorSucceeds() throws Exception {
        ParkingSpace space = new ParkingSpace();
        space.setSpaceNumber("CM-L2S01");
        space.setFloor(2); // Valid floor
        space.setFacility(facility);
        space.setHourlyRate(BigDecimal.valueOf(250.00));

        when(parkingSpaceRepository.findBySpaceNumber("CM-L2S01")).thenReturn(Optional.empty());
        when(parkingSpaceRepository.save(any(ParkingSpace.class))).thenAnswer(i -> i.getArgument(0));

        ParkingSpace saved = spaceService.createParkingSpace(space);
        assertNotNull(saved);
        assertEquals(2, saved.getFloor());
        assertEquals("CM-L2S01", saved.getSpaceNumber());
    }

    @Test
    @DisplayName("Create space with floor exceeding facility totalFloors is rejected")
    void testCreateSpaceFloorExceedingFacilityFloorsThrows() {
        ParkingSpace space = new ParkingSpace();
        space.setSpaceNumber("CM-L5S01");
        space.setFloor(5); // Facility only has 3 floors!
        space.setFacility(facility);

        when(parkingSpaceRepository.findBySpaceNumber("CM-L5S01")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> spaceService.createParkingSpace(space));
        assertTrue(ex.getMessage().contains("Invalid floor level: 5"));
        assertTrue(ex.getMessage().contains("only has 3 floor level(s)"));
        verify(parkingSpaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create space with floor 0 or negative is rejected")
    void testCreateSpaceZeroOrNegativeFloorThrows() {
        ParkingSpace space = new ParkingSpace();
        space.setSpaceNumber("CM-L0S01");
        space.setFloor(0);
        space.setFacility(facility);

        when(parkingSpaceRepository.findBySpaceNumber("CM-L0S01")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> spaceService.createParkingSpace(space));
        assertTrue(ex.getMessage().contains("Invalid floor level: 0"));
        verify(parkingSpaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update space with floor exceeding facility totalFloors is rejected")
    void testUpdateSpaceFloorExceedingFacilityFloorsThrows() throws Exception {
        ParkingSpace existing = new ParkingSpace();
        existing.setId(1L);
        existing.setSpaceNumber("CM-L1S01");
        existing.setFloor(1);
        existing.setFacility(facility);

        when(parkingSpaceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(parkingSpaceRepository.findBySpaceNumberAndIdNot(any(), any())).thenReturn(Optional.empty());

        ParkingSpace update = new ParkingSpace();
        update.setSpaceNumber("CM-L1S01");
        update.setFloor(4); // Exceeds 3 floors

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> spaceService.updateParkingSpace(1L, update));
        assertTrue(ex.getMessage().contains("Invalid floor level: 4"));
        verify(parkingSpaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Extract zone code formats ZONE-A, ZONE-B, VIP-ZONE and defaults cleanly")
    void testExtractZoneCode() {
        assertEquals("A", ParkingSpaceService.extractZoneCode("ZONE-A"));
        assertEquals("B", ParkingSpaceService.extractZoneCode("ZONE-B"));
        assertEquals("VIP", ParkingSpaceService.extractZoneCode("VIP-ZONE"));
        assertEquals("C", ParkingSpaceService.extractZoneCode("C"));
        assertEquals("A", ParkingSpaceService.extractZoneCode(null));
        assertEquals("A", ParkingSpaceService.extractZoneCode("   "));
    }

    @Test
    @DisplayName("Generate next space code when no existing spaces yields 01 slot")
    void testGenerateNextSpaceNumber_NoExistingSpaces() {
        when(parkingSpaceRepository.findSpaceNumbersByFacilityIdAndPrefix(10L, "F10-L1A-%"))
                .thenReturn(java.util.Collections.emptyList());
        when(parkingSpaceRepository.findBySpaceNumber("F10-L1A-01")).thenReturn(Optional.empty());

        String generated = spaceService.generateNextSpaceNumber(10L, 1, "ZONE-A");
        assertEquals("F10-L1A-01", generated);
    }

    @Test
    @DisplayName("Generate next space code increments slot sequentially")
    void testGenerateNextSpaceNumber_IncrementsSequentially() {
        when(parkingSpaceRepository.findSpaceNumbersByFacilityIdAndPrefix(10L, "F10-L1A-%"))
                .thenReturn(java.util.List.of("F10-L1A-01", "F10-L1A-02"));
        when(parkingSpaceRepository.findBySpaceNumber("F10-L1A-03")).thenReturn(Optional.empty());

        String generated = spaceService.generateNextSpaceNumber(10L, 1, "ZONE-A");
        assertEquals("F10-L1A-03", generated);
    }

    @Test
    @DisplayName("Generate next space code for Floor 2 and ZONE-B")
    void testGenerateNextSpaceNumber_Floor2ZoneB() {
        when(parkingSpaceRepository.findSpaceNumbersByFacilityIdAndPrefix(10L, "F10-L2B-%"))
                .thenReturn(java.util.List.of("F10-L2B-01"));
        when(parkingSpaceRepository.findBySpaceNumber("F10-L2B-02")).thenReturn(Optional.empty());

        String generated = spaceService.generateNextSpaceNumber(10L, 2, "ZONE-B");
        assertEquals("F10-L2B-02", generated);
    }
}

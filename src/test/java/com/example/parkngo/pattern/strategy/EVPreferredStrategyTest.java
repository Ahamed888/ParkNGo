package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EVPreferredStrategyTest {

    private EVPreferredStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EVPreferredStrategy();
    }

    @Test
    @DisplayName("Strategy type is EV_PREFERRED")
    void testStrategyType() {
        assertEquals(AllocationStrategyType.EV_PREFERRED, strategy.getStrategyType());
    }

    @Test
    @DisplayName("Prioritizes EV charging bay over standard bay")
    void testPrioritizeEVBay() {
        ParkingSpace standardSpace = createSpace(1L, "F1-L1A-01", 1, "Zone A", false, false, "STANDARD");
        ParkingSpace evSpace = createSpace(2L, "F1-L2A-05", 2, "Zone A", true, false, "EV_CHARGING");

        Optional<ParkingSpace> result = strategy.allocate(List.of(standardSpace, evSpace), AllocationCriteria.ev());

        assertTrue(result.isPresent());
        assertEquals("F1-L2A-05", result.get().getSpaceNumber());
        assertTrue(result.get().getHasEVCharging());
    }

    @Test
    @DisplayName("Gracefully falls back to standard bay if no EV bay available")
    void testFallbackWhenNoEVAvailable() {
        ParkingSpace s1 = createSpace(1L, "F1-L1A-01", 1, "Zone A", false, false, "STANDARD");
        ParkingSpace s2 = createSpace(2L, "F1-L2A-01", 2, "Zone A", false, false, "STANDARD");

        Optional<ParkingSpace> result = strategy.allocate(List.of(s1, s2), AllocationCriteria.ev());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-01", result.get().getSpaceNumber());
    }

    @Test
    @DisplayName("Returns empty optional when space list is empty")
    void testEmptyList() {
        Optional<ParkingSpace> result = strategy.allocate(List.of(), AllocationCriteria.ev());
        assertTrue(result.isEmpty());
    }

    private ParkingSpace createSpace(Long id, String spaceNumber, int floor, String zone, boolean hasEV, boolean isAccessible, String parkingType) {
        ParkingSpace space = new ParkingSpace();
        space.setId(id);
        space.setSpaceNumber(spaceNumber);
        space.setFloor(floor);
        space.setZone(zone);
        space.setHasEVCharging(hasEV);
        space.setIsAccessible(isAccessible);
        space.setParkingType(parkingType);
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        return space;
    }
}

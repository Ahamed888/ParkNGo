package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccessiblePriorityStrategyTest {

    private AccessiblePriorityStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AccessiblePriorityStrategy();
    }

    @Test
    @DisplayName("Strategy type is ACCESSIBLE_PRIORITY")
    void testStrategyType() {
        assertEquals(AllocationStrategyType.ACCESSIBLE_PRIORITY, strategy.getStrategyType());
    }

    @Test
    @DisplayName("Prioritizes accessible bay over regular bays")
    void testPrioritizesAccessibleBay() {
        ParkingSpace regular = createSpace(1L, "F1-L1A-01", 1, "Zone A", false, false, "STANDARD");
        ParkingSpace accessible = createSpace(2L, "F1-L1A-04", 1, "Zone A", false, true, "ACCESSIBLE");

        Optional<ParkingSpace> result = strategy.allocate(List.of(regular, accessible), AllocationCriteria.accessible());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-04", result.get().getSpaceNumber());
        assertTrue(result.get().getIsAccessible());
    }

    @Test
    @DisplayName("Falls back to ground floor bay when no accessible bay is available")
    void testFallbackToGroundFloor() {
        ParkingSpace floor2 = createSpace(1L, "F1-L2A-01", 2, "Zone A", false, false, "STANDARD");
        ParkingSpace floor1 = createSpace(2L, "F1-L1B-01", 1, "Zone B", false, false, "STANDARD");

        Optional<ParkingSpace> result = strategy.allocate(List.of(floor2, floor1), AllocationCriteria.accessible());

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getFloor());
        assertEquals("F1-L1B-01", result.get().getSpaceNumber());
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

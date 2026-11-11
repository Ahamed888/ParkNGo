package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VIPPriorityStrategyTest {

    private VIPPriorityStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new VIPPriorityStrategy();
    }

    @Test
    @DisplayName("Strategy type is VIP_PRIORITY")
    void testStrategyType() {
        assertEquals(AllocationStrategyType.VIP_PRIORITY, strategy.getStrategyType());
    }

    @Test
    @DisplayName("Prioritizes VIP designated space")
    void testPrioritizeVIPSpace() {
        ParkingSpace standard = createSpace(1L, "F1-L1A-01", 1, "Zone A", "STANDARD", "GENERAL");
        ParkingSpace vip = createSpace(2L, "F1-L1A-02", 1, "Zone A", "VIP", "RESERVED_VIP");

        Optional<ParkingSpace> result = strategy.allocate(List.of(standard, vip), AllocationCriteria.vip());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-02", result.get().getSpaceNumber());
    }

    @Test
    @DisplayName("Falls back to standard space when VIP is not available")
    void testFallbackWhenNoVIP() {
        ParkingSpace standard = createSpace(1L, "F1-L1A-01", 1, "Zone A", "STANDARD", "GENERAL");

        Optional<ParkingSpace> result = strategy.allocate(List.of(standard), AllocationCriteria.vip());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-01", result.get().getSpaceNumber());
    }

    private ParkingSpace createSpace(Long id, String spaceNumber, int floor, String zone, String parkingType, String allocationType) {
        ParkingSpace space = new ParkingSpace();
        space.setId(id);
        space.setSpaceNumber(spaceNumber);
        space.setFloor(floor);
        space.setZone(zone);
        space.setParkingType(parkingType);
        space.setAllocationType(allocationType);
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        return space;
    }
}

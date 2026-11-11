package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NearestEntranceStrategyTest {

    private NearestEntranceStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NearestEntranceStrategy();
    }

    @Test
    @DisplayName("Strategy type is NEAREST_ENTRANCE")
    void testStrategyType() {
        assertEquals(AllocationStrategyType.NEAREST_ENTRANCE, strategy.getStrategyType());
    }

    @Test
    @DisplayName("Allocates bay on lowest floor and zone A first")
    void testAllocateLowestFloorFirst() {
        ParkingSpace s1 = createSpace(1L, "F1-L2A-01", 2, "Zone A");
        ParkingSpace s2 = createSpace(2L, "F1-L1B-01", 1, "Zone B");
        ParkingSpace s3 = createSpace(3L, "F1-L1A-02", 1, "Zone A");

        Optional<ParkingSpace> result = strategy.allocate(List.of(s1, s2, s3), AllocationCriteria.standard());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-02", result.get().getSpaceNumber());
    }

    @Test
    @DisplayName("Respects preferred floor when requested")
    void testAllocateWithPreferredFloor() {
        ParkingSpace s1 = createSpace(1L, "F1-L1A-01", 1, "Zone A");
        ParkingSpace s2 = createSpace(2L, "F1-L3A-01", 3, "Zone A");

        Optional<ParkingSpace> result = strategy.allocate(List.of(s1, s2), AllocationCriteria.onFloor(3));

        assertTrue(result.isPresent());
        assertEquals(3, result.get().getFloor());
        assertEquals("F1-L3A-01", result.get().getSpaceNumber());
    }

    @Test
    @DisplayName("Returns empty optional when space list is empty")
    void testAllocateEmptyList() {
        Optional<ParkingSpace> result = strategy.allocate(List.of(), AllocationCriteria.standard());
        assertTrue(result.isEmpty());
    }

    private ParkingSpace createSpace(Long id, String spaceNumber, int floor, String zone) {
        ParkingSpace space = new ParkingSpace();
        space.setId(id);
        space.setSpaceNumber(spaceNumber);
        space.setFloor(floor);
        space.setZone(zone);
        space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
        return space;
    }
}

package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// [VIVA - Strategy Pattern Unit Test]: Verifies dynamic strategy selection (EV, VIP, Accessible, Default) based on runtime criteria.
class SpaceAllocationContextTest {

    private SpaceAllocationContext context;
    private NearestEntranceStrategy nearestStrategy;
    private EVPreferredStrategy evStrategy;
    private AccessiblePriorityStrategy accessibleStrategy;
    private VIPPriorityStrategy vipStrategy;

    @BeforeEach
    void setUp() {
        nearestStrategy = new NearestEntranceStrategy();
        evStrategy = new EVPreferredStrategy();
        accessibleStrategy = new AccessiblePriorityStrategy();
        vipStrategy = new VIPPriorityStrategy();

        context = new SpaceAllocationContext(List.of(nearestStrategy, evStrategy, accessibleStrategy, vipStrategy));
    }

    @Test
    @DisplayName("Dynamically selects EV strategy when EV required")
    void testDetermineStrategyEV() {
        SpaceAllocationStrategy selected = context.determineStrategy(AllocationCriteria.ev());
        assertEquals(AllocationStrategyType.EV_PREFERRED, selected.getStrategyType());
    }

    @Test
    @DisplayName("Dynamically selects Accessible strategy when accessible required")
    void testDetermineStrategyAccessible() {
        SpaceAllocationStrategy selected = context.determineStrategy(AllocationCriteria.accessible());
        assertEquals(AllocationStrategyType.ACCESSIBLE_PRIORITY, selected.getStrategyType());
    }

    @Test
    @DisplayName("Dynamically selects VIP strategy when VIP requested")
    void testDetermineStrategyVIP() {
        SpaceAllocationStrategy selected = context.determineStrategy(AllocationCriteria.vip());
        assertEquals(AllocationStrategyType.VIP_PRIORITY, selected.getStrategyType());
    }

    @Test
    @DisplayName("Defaults to Nearest Entrance strategy for standard request")
    void testDetermineStrategyDefault() {
        SpaceAllocationStrategy selected = context.determineStrategy(AllocationCriteria.standard());
        assertEquals(AllocationStrategyType.NEAREST_ENTRANCE, selected.getStrategyType());
    }

    @Test
    @DisplayName("Allocates space correctly through context delegation")
    void testAllocateSpaceThroughContext() {
        ParkingSpace s1 = new ParkingSpace();
        s1.setId(1L);
        s1.setSpaceNumber("F1-L1A-01");
        s1.setFloor(1);
        s1.setZone("Zone A");
        s1.setHasEVCharging(true);

        ParkingSpace s2 = new ParkingSpace();
        s2.setId(2L);
        s2.setSpaceNumber("F1-L1A-02");
        s2.setFloor(1);
        s2.setZone("Zone A");
        s2.setHasEVCharging(false);

        Optional<ParkingSpace> result = context.allocateSpace(List.of(s2, s1), AllocationCriteria.ev());

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-01", result.get().getSpaceNumber());
    }

    @Test
    @DisplayName("Explicit strategy override works")
    void testExplicitStrategyOverride() {
        ParkingSpace s1 = new ParkingSpace();
        s1.setId(1L);
        s1.setSpaceNumber("F1-L1A-01");
        s1.setFloor(1);
        s1.setZone("Zone A");

        Optional<ParkingSpace> result = context.allocateSpace(
                List.of(s1), AllocationCriteria.standard(), AllocationStrategyType.NEAREST_ENTRANCE);

        assertTrue(result.isPresent());
        assertEquals("F1-L1A-01", result.get().getSpaceNumber());
    }
}

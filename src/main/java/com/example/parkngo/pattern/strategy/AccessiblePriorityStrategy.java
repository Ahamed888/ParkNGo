package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ConcreteStrategy: Prioritizes designated accessible (handicap) parking bays (isAccessible = true).
 * Prioritizes ground floor (Floor 1) bays closest to entrance ramps and elevators.
 * Falls back to nearest ground-level bay if all designated accessible bays are occupied.
 */
// [VIVA - Strategy 3 (Accessible)]: Prioritizes wheelchair-accessible bays (isAccessible == true) on ground levels near entrance ramps.
@Component
public class AccessiblePriorityStrategy implements SpaceAllocationStrategy {

    private static final Comparator<ParkingSpace> COMPARATOR = Comparator
            .comparing((ParkingSpace s) -> s.getFloor() != null ? s.getFloor() : 1)
            .thenComparing(s -> s.getZone() != null ? s.getZone() : "")
            .thenComparing(s -> s.getSpaceNumber() != null ? s.getSpaceNumber() : "");

    @Override
    public Optional<ParkingSpace> allocate(List<ParkingSpace> availableSpaces, AllocationCriteria criteria) {
        if (availableSpaces == null || availableSpaces.isEmpty()) {
            return Optional.empty();
        }

        Optional<ParkingSpace> accessibleSpace = availableSpaces.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsAccessible())
                        || "ACCESSIBLE".equalsIgnoreCase(s.getParkingType())
                        || "HANDICAPPED".equalsIgnoreCase(s.getParkingType()))
                .min(COMPARATOR);

        if (accessibleSpace.isPresent()) {
            return accessibleSpace;
        }

        // Fallback to ground floor (Floor 1) nearest bay if possible
        return availableSpaces.stream()
                .filter(s -> s.getFloor() != null && s.getFloor() == 1)
                .min(COMPARATOR)
                .or(() -> availableSpaces.stream().min(COMPARATOR));
    }

    @Override
    public AllocationStrategyType getStrategyType() {
        return AllocationStrategyType.ACCESSIBLE_PRIORITY;
    }
}

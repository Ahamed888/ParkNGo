package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ConcreteStrategy: Prioritizes bays equipped with EV charging stations (hasEVCharging = true).
 * If no EV-equipped bay is available, gracefully falls back to the nearest standard bay.
 */
// [VIVA - Strategy 2 (EV)]: Filters and prioritizes bays equipped with EV charging stations (hasEVCharging == true).
@Component
public class EVPreferredStrategy implements SpaceAllocationStrategy {

    private static final Comparator<ParkingSpace> COMPARATOR = Comparator
            .comparing((ParkingSpace s) -> s.getFloor() != null ? s.getFloor() : 1)
            .thenComparing(s -> s.getZone() != null ? s.getZone() : "")
            .thenComparing(s -> s.getSpaceNumber() != null ? s.getSpaceNumber() : "");

    @Override
    public Optional<ParkingSpace> allocate(List<ParkingSpace> availableSpaces, AllocationCriteria criteria) {
        if (availableSpaces == null || availableSpaces.isEmpty()) {
            return Optional.empty();
        }

        Optional<ParkingSpace> evSpace = availableSpaces.stream()
                .filter(s -> Boolean.TRUE.equals(s.getHasEVCharging())
                        || "EV_CHARGING".equalsIgnoreCase(s.getParkingType())
                        || "ELECTRIC".equalsIgnoreCase(s.getParkingType()))
                .min(COMPARATOR);

        if (evSpace.isPresent()) {
            return evSpace;
        }

        // Graceful fallback to nearest available standard bay
        return availableSpaces.stream().min(COMPARATOR);
    }

    @Override
    public AllocationStrategyType getStrategyType() {
        return AllocationStrategyType.EV_PREFERRED;
    }
}

package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ConcreteStrategy: Prioritizes the bay closest to the entrance/exit.
 * In a multi-storey or multi-zone facility, this selects the lowest floor level first,
 * then zone alphabetically (Zone A before Zone B), and then lowest space number.
 */
// [VIVA - Strategy 1 (Default)]: Allocates closest bay by sorting by lowest floor, nearest zone, and lowest space number.
@Component
public class NearestEntranceStrategy implements SpaceAllocationStrategy {

    private static final Comparator<ParkingSpace> PROXIMITY_COMPARATOR = Comparator
            .comparing((ParkingSpace s) -> s.getFloor() != null ? s.getFloor() : 1)
            .thenComparing(s -> s.getZone() != null ? s.getZone() : "")
            .thenComparing(s -> s.getSpaceNumber() != null ? s.getSpaceNumber() : "");

    @Override
    public Optional<ParkingSpace> allocate(List<ParkingSpace> availableSpaces, AllocationCriteria criteria) {
        if (availableSpaces == null || availableSpaces.isEmpty()) {
            return Optional.empty();
        }

        if (criteria != null && criteria.getPreferredFloor() != null) {
            Optional<ParkingSpace> onPreferredFloor = availableSpaces.stream()
                    .filter(s -> criteria.getPreferredFloor().equals(s.getFloor()))
                    .min(PROXIMITY_COMPARATOR);
            if (onPreferredFloor.isPresent()) {
                return onPreferredFloor;
            }
        }

        return availableSpaces.stream().min(PROXIMITY_COMPARATOR);
    }

    @Override
    public AllocationStrategyType getStrategyType() {
        return AllocationStrategyType.NEAREST_ENTRANCE;
    }
}

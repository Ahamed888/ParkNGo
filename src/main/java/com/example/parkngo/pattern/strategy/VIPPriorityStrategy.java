package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ConcreteStrategy: Prioritizes designated VIP bays or premium spaces.
 * Falls back to the nearest standard bay if VIP bays are fully booked.
 */
// [VIVA - Strategy 4 (VIP)]: Prioritizes RESERVED_VIP designated bays for executive/subscription parking passes.
@Component
public class VIPPriorityStrategy implements SpaceAllocationStrategy {

    private static final Comparator<ParkingSpace> COMPARATOR = Comparator
            .comparing((ParkingSpace s) -> s.getFloor() != null ? s.getFloor() : 1)
            .thenComparing(s -> s.getZone() != null ? s.getZone() : "")
            .thenComparing(s -> s.getSpaceNumber() != null ? s.getSpaceNumber() : "");

    @Override
    public Optional<ParkingSpace> allocate(List<ParkingSpace> availableSpaces, AllocationCriteria criteria) {
        if (availableSpaces == null || availableSpaces.isEmpty()) {
            return Optional.empty();
        }

        Optional<ParkingSpace> vipSpace = availableSpaces.stream()
                .filter(s -> "VIP".equalsIgnoreCase(s.getParkingType())
                        || "RESERVED_VIP".equalsIgnoreCase(s.getAllocationType()))
                .min(COMPARATOR);

        if (vipSpace.isPresent()) {
            return vipSpace;
        }

        return availableSpaces.stream().min(COMPARATOR);
    }

    @Override
    public AllocationStrategyType getStrategyType() {
        return AllocationStrategyType.VIP_PRIORITY;
    }
}

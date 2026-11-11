package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface defining the contract for parking space allocation algorithms
 * within a parking facility.
 */
// [VIVA - Strategy Pattern]: Common algorithm contract; allows dynamic swapping of space allocation rules without modifying facility code.
public interface SpaceAllocationStrategy {
    Optional<ParkingSpace> allocate(List<ParkingSpace> availableSpaces, AllocationCriteria criteria);

    AllocationStrategyType getStrategyType();
}

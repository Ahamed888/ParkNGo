package com.example.parkngo.pattern.strategy;

import com.example.parkngo.space.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context in the Strategy Pattern: Holds a registry of all concrete strategies
 * and resolves the appropriate strategy dynamically at runtime based on criteria or explicit request.
 */
// [VIVA - Strategy Context]: Holds strategy registry in EnumMap; eliminates if-else chains by resolving algorithms at runtime.
@Component
public class SpaceAllocationContext {

    private final Map<AllocationStrategyType, SpaceAllocationStrategy> strategyMap = new EnumMap<>(AllocationStrategyType.class);
    private SpaceAllocationStrategy defaultStrategy;

    public SpaceAllocationContext(List<SpaceAllocationStrategy> strategies) {
        if (strategies != null) {
            for (SpaceAllocationStrategy strategy : strategies) {
                strategyMap.put(strategy.getStrategyType(), strategy);
                if (strategy.getStrategyType() == AllocationStrategyType.NEAREST_ENTRANCE) {
                    this.defaultStrategy = strategy;
                }
            }
            if (this.defaultStrategy == null && !strategies.isEmpty()) {
                this.defaultStrategy = strategies.get(0);
            }
        }
    }

    /**
     * Dynamically determines the best strategy from criteria and allocates an optimal space.
     */
    public Optional<ParkingSpace> allocateSpace(List<ParkingSpace> candidateSpaces, AllocationCriteria criteria) {
        SpaceAllocationStrategy strategy = determineStrategy(criteria);
        if (strategy == null) {
            return candidateSpaces != null && !candidateSpaces.isEmpty() ? Optional.of(candidateSpaces.get(0)) : Optional.empty();
        }
        return strategy.allocate(candidateSpaces, criteria);
    }

    /**
     * Executes allocation using an explicitly requested strategy type.
     */
    public Optional<ParkingSpace> allocateSpace(List<ParkingSpace> candidateSpaces, AllocationCriteria criteria, AllocationStrategyType strategyType) {
        SpaceAllocationStrategy strategy = strategyMap.getOrDefault(strategyType, defaultStrategy);
        if (strategy == null) {
            return candidateSpaces != null && !candidateSpaces.isEmpty() ? Optional.of(candidateSpaces.get(0)) : Optional.empty();
        }
        return strategy.allocate(candidateSpaces, criteria);
    }

    /**
     * Resolves the strategy dynamically from the criteria attributes.
     */
    // [VIVA - Dynamic Strategy Resolution]: Inspects criteria flags (EV, VIP, Accessible) and selects the matching strategy.
    public SpaceAllocationStrategy determineStrategy(AllocationCriteria criteria) {
        if (criteria == null) {
            return defaultStrategy;
        }
        if (criteria.isRequiresAccessible()) {
            return strategyMap.getOrDefault(AllocationStrategyType.ACCESSIBLE_PRIORITY, defaultStrategy);
        }
        if (criteria.isRequiresEV()) {
            return strategyMap.getOrDefault(AllocationStrategyType.EV_PREFERRED, defaultStrategy);
        }
        if (criteria.isVipRequested()) {
            return strategyMap.getOrDefault(AllocationStrategyType.VIP_PRIORITY, defaultStrategy);
        }
        return defaultStrategy;
    }

    public SpaceAllocationStrategy getStrategy(AllocationStrategyType type) {
        return strategyMap.getOrDefault(type, defaultStrategy);
    }
}

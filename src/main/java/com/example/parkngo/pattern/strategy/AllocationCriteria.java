package com.example.parkngo.pattern.strategy;

/**
 * Immutable value object encapsulating user and vehicle requirements
 * used by allocation strategies to select the optimal parking bay.
 */
public final class AllocationCriteria {
    private final boolean requiresEV;
    private final boolean requiresAccessible;
    private final boolean vipRequested;
    private final Integer preferredFloor;

    public AllocationCriteria(boolean requiresEV, boolean requiresAccessible, boolean vipRequested, Integer preferredFloor) {
        this.requiresEV = requiresEV;
        this.requiresAccessible = requiresAccessible;
        this.vipRequested = vipRequested;
        this.preferredFloor = preferredFloor;
    }

    public static AllocationCriteria standard() {
        return new AllocationCriteria(false, false, false, null);
    }

    public static AllocationCriteria ev() {
        return new AllocationCriteria(true, false, false, null);
    }

    public static AllocationCriteria accessible() {
        return new AllocationCriteria(false, true, false, null);
    }

    public static AllocationCriteria vip() {
        return new AllocationCriteria(false, false, true, null);
    }

    public static AllocationCriteria onFloor(int floor) {
        return new AllocationCriteria(false, false, false, floor);
    }

    public boolean isRequiresEV() {
        return requiresEV;
    }

    public boolean isRequiresAccessible() {
        return requiresAccessible;
    }

    public boolean isVipRequested() {
        return vipRequested;
    }

    public Integer getPreferredFloor() {
        return preferredFloor;
    }
}

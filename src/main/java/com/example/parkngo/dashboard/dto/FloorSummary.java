package com.example.parkngo.dashboard.dto;

public record FloorSummary(int level, long count, int occupiedPct) {
    public int floor() {
        return level;
    }

    public int getFloor() {
        return level;
    }

    public int occupancyPct() {
        return occupiedPct;
    }

    public int getOccupancyPct() {
        return occupiedPct;
    }

    public int getLevel() {
        return level;
    }

    public long getCount() {
        return count;
    }

    public int getOccupiedPct() {
        return occupiedPct;
    }
}

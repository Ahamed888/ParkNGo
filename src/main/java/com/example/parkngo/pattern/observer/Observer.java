package com.example.parkngo.pattern.observer;

/**
 * Classic Observer pattern participant that reacts to a parking event.
 */
public interface Observer {
    void update(ParkingEvent event);
}

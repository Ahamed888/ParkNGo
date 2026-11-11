package com.example.parkngo.pattern.observer;

/**
 * Classic Observer pattern subject: it maintains observers and tells them
 * when a parking event occurs.
 */
public interface Subject {
    void addObserver(Observer observer);

    void removeObserver(Observer observer);

    void notifyObservers(ParkingEvent event);
}

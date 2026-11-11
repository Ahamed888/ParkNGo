package com.example.parkngo.pattern.observer;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

/** Registers Spring-managed concrete observers with the hand-written singleton subject. */
@Component
public class ObserverRegistrar {
    private final List<Observer> observers;

    public ObserverRegistrar(List<Observer> observers) {
        this.observers = observers;
    }

    @PostConstruct
    public void registerObservers() {
        ParkingEventSubject subject = ParkingEventSubject.getInstance();
        observers.forEach(subject::addObserver);
    }
}

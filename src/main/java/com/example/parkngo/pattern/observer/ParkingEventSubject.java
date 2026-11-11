package com.example.parkngo.pattern.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConcreteSubject for parking events and a classic Singleton.
 */
// [VIVA - GoF Singleton & Subject]: Central event broker; ensures a single global event bus so observer subscriptions are never fragmented.
public class ParkingEventSubject implements Subject {
    private static final Logger log = LoggerFactory.getLogger(ParkingEventSubject.class);

    private static ParkingEventSubject instance;
    private final CopyOnWriteArrayList<Observer> observers = new CopyOnWriteArrayList<>();

    // [VIVA - Singleton Invariant 1]: Private constructor prevents external instantiation.
    private ParkingEventSubject() {
    }

    /**
     * Returns the sole subject instance using lazy initialisation, synchronized for thread safety.
     */
    // [VIVA - Singleton Invariant 2]: Lazy, thread-safe synchronized accessor providing global access to the sole instance.
    public static synchronized ParkingEventSubject getInstance() {
        if (instance == null) {
            instance = new ParkingEventSubject();
        }
        return instance;
    }

    @Override
    public void addObserver(Observer observer) {
        if (observer != null) {
            observers.addIfAbsent(observer);
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(ParkingEvent event) {
        for (Observer observer : observers) {
            try {
                observer.update(event);
            } catch (Exception e) {
                log.error("Parking event observer failed for type={} recipientId={} referenceId={}",
                        event != null ? event.getType() : null,
                        event != null ? event.getRecipientId() : null,
                        event != null ? event.getReferenceId() : null, e);
            }
        }
    }

    /** Convenience operation for publishing an event to all observers. */
    public void publish(ParkingEvent event) {
        notifyObservers(event);
    }

    /**
     * Clears global observer state for unit tests. Shared global state makes
     * Singletons harder to test; tests use this method to avoid leaking observers.
     */
    void clearObservers() {
        observers.clear();
    }
}

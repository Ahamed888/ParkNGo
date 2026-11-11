package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// [VIVA - Singleton & Observer Unit Test]: Verifies that getInstance() returns the identical singleton instance across concurrent threads and delivers events to observers.
class ParkingEventSubjectTest {
    private final ParkingEventSubject subject = ParkingEventSubject.getInstance();

    @AfterEach
    void clearObservers() {
        subject.clearObservers();
    }

    @Test
    void getInstance_returnsSameObject() {
        assertSame(ParkingEventSubject.getInstance(), ParkingEventSubject.getInstance());
    }

    @Test
    void getInstance_isSafeAcrossConcurrentCalls() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<ParkingEventSubject> instances = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                synchronized (instances) {
                    instances.add(ParkingEventSubject.getInstance());
                }
                return null;
            });
        }
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(50, instances.size());
        assertTrue(instances.stream().allMatch(instance -> instance == subject));
    }

    @Test
    void notifyObservers_deliversToRegisteredObserversButNotRemovedOne() {
        List<ParkingEvent> firstEvents = new ArrayList<>();
        List<ParkingEvent> secondEvents = new ArrayList<>();
        Observer first = firstEvents::add;
        Observer second = secondEvents::add;
        ParkingEvent event = event();

        subject.addObserver(first);
        subject.addObserver(second);
        subject.removeObserver(second);
        subject.notifyObservers(event);

        assertEquals(List.of(event), firstEvents);
        assertTrue(secondEvents.isEmpty());
    }

    @Test
    void addObserver_ignoresDuplicateAndNullObservers() {
        List<ParkingEvent> events = new ArrayList<>();
        Observer observer = events::add;

        subject.addObserver(null);
        subject.addObserver(observer);
        subject.addObserver(observer);
        subject.notifyObservers(event());

        assertEquals(1, events.size());
    }

    @Test
    void notifyObservers_continuesWhenAnObserverFails() {
        List<ParkingEvent> events = new ArrayList<>();
        subject.addObserver(ignored -> { throw new IllegalStateException("expected test failure"); });
        subject.addObserver(events::add);

        assertDoesNotThrow(() -> subject.notifyObservers(event()));
        assertEquals(1, events.size());
    }

    private ParkingEvent event() {
        return new ParkingEvent(7L, "Title", "Message", Notification.NotificationType.SYSTEM_ALERT, 9L);
    }
}

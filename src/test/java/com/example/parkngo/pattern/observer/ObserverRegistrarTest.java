package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// [VIVA - Observer Registration Unit Test]: Verifies that Spring automatically wires all concrete observers into the Singleton Subject on startup.
class ObserverRegistrarTest {
    private final ParkingEventSubject subject = ParkingEventSubject.getInstance();

    @AfterEach
    void clearObservers() {
        subject.clearObservers();
    }

    @Test
    void registerObservers_registersEachOnceEvenWhenCalledTwice() {
        Observer first = mock(Observer.class);
        Observer second = mock(Observer.class);
        ObserverRegistrar registrar = new ObserverRegistrar(List.of(first, second));

        registrar.registerObservers();
        registrar.registerObservers();
        ParkingEvent event = new ParkingEvent(1L, "Title", "Message", Notification.NotificationType.SYSTEM_ALERT, 3L);
        subject.publish(event);

        verify(first, times(1)).update(event);
        verify(second, times(1)).update(event);
    }
}

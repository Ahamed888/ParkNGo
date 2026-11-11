package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.Notification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AuditLogObserverTest {
    @Test
    void update_acceptsEventsWithAndWithoutReferenceId() {
        AuditLogObserver observer = new AuditLogObserver();

        assertDoesNotThrow(() -> observer.update(new ParkingEvent(1L, "Title", "Message",
                Notification.NotificationType.SYSTEM_ALERT, 2L)));
        assertDoesNotThrow(() -> observer.update(new ParkingEvent(1L, "Title", "Message",
                Notification.NotificationType.SYSTEM_ALERT, null)));
    }
}

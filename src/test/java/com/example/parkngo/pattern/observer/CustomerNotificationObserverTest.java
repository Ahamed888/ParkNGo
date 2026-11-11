package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.Notification;
import com.example.parkngo.feedback.NotificationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomerNotificationObserverTest {
    @Test
    void update_sendsOneNotificationWithTheEventValues() throws Exception {
        NotificationService notificationService = mock(NotificationService.class);
        CustomerNotificationObserver observer = new CustomerNotificationObserver(notificationService);
        ParkingEvent event = event();

        observer.update(event);

        verify(notificationService).sendNotification(4L, "Payment Received", "Payment complete",
                Notification.NotificationType.PAYMENT_SUCCESSFUL, 12L);
    }

    @Test
    void update_swallowsNotificationServiceFailure() throws Exception {
        NotificationService notificationService = mock(NotificationService.class);
        doThrow(new Exception("database unavailable")).when(notificationService)
                .sendNotification(4L, "Payment Received", "Payment complete",
                        Notification.NotificationType.PAYMENT_SUCCESSFUL, 12L);

        assertDoesNotThrow(() -> new CustomerNotificationObserver(notificationService).update(event()));
    }

    private ParkingEvent event() {
        return new ParkingEvent(4L, "Payment Received", "Payment complete",
                Notification.NotificationType.PAYMENT_SUCCESSFUL, 12L);
    }
}

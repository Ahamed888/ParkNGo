package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** ConcreteObserver that persists customer notifications. */
// [VIVA - Concrete Observer 1]: Reacts to parking events by saving customer in-app notifications without coupling checkout logic.
@Component
public class CustomerNotificationObserver implements Observer {
    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationObserver.class);

    private final NotificationService notificationService;

    public CustomerNotificationObserver(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void update(ParkingEvent event) {
        try {
            notificationService.sendNotification(event.getRecipientId(), event.getTitle(), event.getMessage(),
                    event.getType(), event.getReferenceId());
        } catch (Exception e) {
            log.warn("Failed to create notification for recipientId={} referenceId={}",
                    event.getRecipientId(), event.getReferenceId(), e);
        }
    }
}
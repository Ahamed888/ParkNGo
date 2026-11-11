package com.example.parkngo.pattern.observer;

import com.example.parkngo.feedback.Notification;

/** Immutable event data published by parking workflows. */
public final class ParkingEvent {
    private final Long recipientId;
    private final String title;
    private final String message;
    private final Notification.NotificationType type;
    private final Long referenceId;

    public ParkingEvent(Long recipientId, String title, String message,
                        Notification.NotificationType type, Long referenceId) {
        this.recipientId = recipientId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.referenceId = referenceId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Notification.NotificationType getType() {
        return type;
    }

    public Long getReferenceId() {
        return referenceId;
    }
}

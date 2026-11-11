package com.example.parkngo.feedback;

import com.example.parkngo.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "Notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "UserID", nullable = false)
    private User recipient;

    @NotBlank
    @Column(name = "Title", nullable = false)
    private String title;

    @NotBlank
    @Column(name = "Message", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "Type", nullable = false)
    private NotificationType type = NotificationType.SYSTEM_ALERT;

    @Column(name = "ReferenceID")
    private Long referenceId;

    @Column(name = "IsRead", nullable = false)
    private Boolean isRead = false;

    @Column(name = "DeliveryStatus", length = 20)
    private String deliveryStatus = "PENDING";

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "SentAt")
    private LocalDateTime sentAt;

    public enum NotificationType {
        RESERVATION_CONFIRMED,
        RESERVATION_MODIFIED,
        RESERVATION_CANCELLED,
        PAYMENT_SUCCESSFUL,
        PAYMENT_FAILED,
        OVERSTAY_ALERT,
        SYSTEM_ALERT
    }

    public Notification() {}

    public Notification(Long id, User recipient, String title, String message, NotificationType type, Long referenceId, Boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.recipient = recipient;
        this.title = title;
        this.message = message;
        this.type = type;
        this.referenceId = referenceId;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}

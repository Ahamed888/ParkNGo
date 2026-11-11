package com.example.parkngo.feedback;

import com.example.parkngo.user.User;
import com.example.parkngo.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<EmailService> emailServiceProvider;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ObjectProvider<EmailService> emailServiceProvider) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailServiceProvider = emailServiceProvider;
    }

    public Notification sendNotification(Long recipientId, String title, String message, Notification.NotificationType type, Long referenceId) throws Exception {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new Exception("Recipient user not found"));

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        emailServiceProvider.ifAvailable(emailService ->
                emailService.sendNotificationEmail(recipient.getEmail(), title, message));
        return saved;
    }

    public Notification sendReservationNotification(Long customerId, String title, String message, Notification.NotificationType type, Long reservationId) throws Exception {
        return sendNotification(customerId, title, message, type, reservationId);
    }

    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalse(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    public void markAsRead(Long notificationId) throws Exception {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new Exception("Notification not found"));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }
}

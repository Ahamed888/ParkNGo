package com.example.parkngo.feedback;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    @Test void notificationAndPasswordResetReachMailSender() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage notification = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage reset = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(notification, reset);
        MailProperties properties = new MailProperties();
        properties.setUsername("sender@example.com");
        EmailService service = new EmailService(sender, properties);
        service.sendNotificationEmail("customer@example.com", "Reservation confirmed", "Your booking is confirmed.");
        service.sendPasswordResetEmail("customer@example.com", "https://example.com/reset?token=test", 30);
        verify(sender).send(notification);
        verify(sender).send(reset);
        assertThat(notification.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
        assertThat(notification.getFrom()[0].toString()).contains("sender@example.com");
        assertThat(notification.getSubject()).isEqualTo("Reservation confirmed");
        assertThat(reset.getSubject()).isEqualTo("Reset your ParkNGo password");
        notification.saveChanges();
        reset.saveChanges();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        reset.writeTo(body);
        assertThat(body.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("multipart/", "parkngo-mark", "test");
    }

    @Test void smtpFailureDoesNotBreakNotificationFlow() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(MimeMessage.class));
        MailProperties properties = new MailProperties();
        properties.setUsername("sender@example.com");
        assertThatCode(() -> new EmailService(sender, properties)
            .sendNotificationEmail("customer@example.com", "Booking", "Confirmed")).doesNotThrowAnyException();
        verify(sender).send(any(MimeMessage.class));
    }
}

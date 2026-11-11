package com.example.parkngo.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.core.io.ClassPathResource;

@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Async("emailTaskExecutor")
    public void sendNotificationEmail(String recipient, String title, String message) {
        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipping notification email because the recipient address is blank");
            return;
        }

        try {
            MimeMessage email = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(email, true, "UTF-8");
            helper.setTo(recipient);
            helper.setFrom(mailProperties.getUsername(), "ParkNGo");
            helper.setSubject(title);
            helper.setText(message, buildBrandedHtml(title, message));
            helper.addInline("parkngo-mark", new ClassPathResource("static/images/parkngo-mark.svg"), "image/svg+xml");
            mailSender.send(email);
            log.info("Notification email sent to {}", recipient);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            // Keep email delivery failures from breaking the in-app notification flow.
            log.warn("Could not send notification email to {}: {}", recipient, exception.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String recipient, String resetLink, long expiryMinutes) {
        if (recipient == null || recipient.isBlank() || resetLink == null || resetLink.isBlank()) {
            log.warn("Skipping password reset email because required delivery details are missing");
            return;
        }

        try {
            MimeMessage email = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(email, true, "UTF-8");
            helper.setTo(recipient);
            helper.setFrom(mailProperties.getUsername(), "ParkNGo");
            helper.setSubject("Reset your ParkNGo password");
            String plainText = "A password reset was requested for your ParkNGo account.\n\n" +
                    "Reset your password: " + resetLink + "\n\n" +
                    "This link expires in " + expiryMinutes + " minutes and can be used once. " +
                    "If you did not request this reset, you can ignore this email.";
            helper.setText(plainText, buildPasswordResetHtml(resetLink, expiryMinutes));
            helper.addInline("parkngo-mark", new ClassPathResource("static/images/parkngo-mark.svg"), "image/svg+xml");
            mailSender.send(email);
            log.info("Password reset email sent");
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            log.warn("Could not send password reset email: {}", exception.getMessage());
        }
    }

    private String buildPasswordResetHtml(String resetLink, long expiryMinutes) {
        String safeLink = HtmlUtils.htmlEscape(resetLink);
        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:24px;background:#f3f6f9;font-family:Arial,Helvetica,sans-serif;color:#0f1b2a;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
                    <tr><td style="padding:22px 28px;background:#0b1c2d;">
                      <table role="presentation" cellspacing="0" cellpadding="0"><tr>
                        <td style="vertical-align:middle;padding-right:12px;"><img src="cid:parkngo-mark" width="40" height="40" alt="ParkNGo logo" style="display:block;border:0;"></td>
                        <td style="vertical-align:middle;font-size:23px;font-weight:700;color:#ffffff;">Park<span style="color:#f7b928;">N</span>Go</td>
                      </tr></table>
                    </td></tr>
                    <tr><td style="padding:30px 28px;">
                      <h1 style="margin:0 0 14px;font-size:22px;color:#0b1c2d;">Reset your password</h1>
                      <p style="margin:0 0 22px;font-size:15px;line-height:1.65;color:#334155;">We received a request to reset your ParkNGo password.</p>
                      <p style="margin:0 0 24px;"><a href="%s" style="display:inline-block;padding:12px 20px;border-radius:8px;background:#f7b928;color:#0b1c2d;text-decoration:none;font-weight:700;">Reset Password</a></p>
                      <p style="margin:0 0 10px;font-size:14px;line-height:1.6;color:#475569;">This secure link expires in %d minutes and can be used only once.</p>
                      <p style="margin:0;font-size:14px;line-height:1.6;color:#475569;">If you did not request a password reset, you can safely ignore this email.</p>
                    </td></tr>
                    <tr><td style="padding:15px 28px;border-top:1px solid #e2e8f0;font-size:12px;color:#64748b;">ParkNGo Sri Lanka</td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeLink, expiryMinutes);
    }

    private String buildBrandedHtml(String title, String message) {
        String safeTitle = HtmlUtils.htmlEscape(title == null ? "ParkNGo notification" : title);
        String safeMessage = HtmlUtils.htmlEscape(message == null ? "" : message)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "<br>");

        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:24px;background:#f3f6f9;font-family:Arial,Helvetica,sans-serif;color:#0f1b2a;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
                    <tr><td style="padding:22px 28px;background:#0b1c2d;">
                      <table role="presentation" cellspacing="0" cellpadding="0"><tr>
                        <td style="vertical-align:middle;padding-right:12px;"><img src="cid:parkngo-mark" width="40" height="40" alt="ParkNGo logo" style="display:block;border:0;"></td>
                        <td style="vertical-align:middle;font-size:23px;font-weight:700;letter-spacing:-.4px;color:#ffffff;">Park<span style="color:#f7b928;">N</span>Go</td>
                      </tr></table>
                    </td></tr>
                    <tr><td style="padding:30px 28px 20px;">
                      <h1 style="margin:0 0 18px;font-size:21px;line-height:1.35;color:#0b1c2d;">%s</h1>
                      <div style="font-size:15px;line-height:1.7;color:#334155;">%s</div>
                      <p style="margin:28px 0 0;font-size:14px;line-height:1.6;color:#334155;">With thanks,<br><strong style="color:#0b1c2d;">The ParkNGo Team</strong></p>
                    </td></tr>
                    <tr><td style="padding:15px 28px;border-top:1px solid #e2e8f0;font-size:12px;color:#64748b;">ParkNGo Sri Lanka</td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeTitle, safeMessage);
    }
}

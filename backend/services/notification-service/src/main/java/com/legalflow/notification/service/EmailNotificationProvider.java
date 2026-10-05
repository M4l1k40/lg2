package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationProvider(JavaMailSender mailSender,
                                     @Value("${notifications.mail.from:no-reply@legalflow.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, NotificationRecipientContact recipient) {
        if (recipient.email() == null || recipient.email().isBlank()) {
            throw new NotificationDeliveryException("Recipient email is missing.");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(recipient.email());
        mail.setSubject(notification.getSubject());
        mail.setText(notification.getMessage() + System.lineSeparator() + System.lineSeparator()
                + "Notification date: " + notification.getCreatedAt());
        try {
            mailSender.send(mail);
        } catch (MailException exception) {
            throw new NotificationDeliveryException("Email provider failed to send notification.", exception);
        }
    }
}
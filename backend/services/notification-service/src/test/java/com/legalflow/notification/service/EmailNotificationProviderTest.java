package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class EmailNotificationProviderTest {

    @Test
    void sendsEmailWithSubjectMessageRecipientAndNotificationDate() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailNotificationProvider provider = new EmailNotificationProvider(mailSender, "no-reply@example.test");
        Notification notification = notification();

        provider.send(notification, new NotificationRecipientContact("Lawyer", "lawyer@example.test", null));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals(NotificationChannel.EMAIL, provider.channel());
        assertEquals("no-reply@example.test", message.getFrom());
        assertEquals("lawyer@example.test", message.getTo()[0]);
        assertEquals("Consultation update", message.getSubject());
        assertTrue(message.getText().contains("Your consultation was updated."));
        assertTrue(message.getText().contains("2026-10-04T12:00"));
    }

    @Test
    void convertsSmtpFailureToSafeDeliveryError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP failure with private configuration details"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        EmailNotificationProvider provider = new EmailNotificationProvider(mailSender, "no-reply@example.test");

        NotificationDeliveryException exception = assertThrows(NotificationDeliveryException.class,
                () -> provider.send(notification(),
                        new NotificationRecipientContact("Lawyer", "lawyer@example.test", null)));

        assertEquals("Email provider failed to send notification.", exception.getMessage());
    }

    private Notification notification() {
        Notification notification = new Notification(UUID.randomUUID(), UUID.randomUUID(),
                NotificationRecipientType.LAWYER, NotificationChannel.EMAIL, "Consultation update",
                "Consultation update", "Your consultation was updated.", NotificationType.CASE_STATUS_CHANGED,
                null, null);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 10, 4, 12, 0));
        return notification;
    }
}
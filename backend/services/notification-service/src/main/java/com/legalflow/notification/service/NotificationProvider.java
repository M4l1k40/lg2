package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;

public interface NotificationProvider {

    NotificationChannel channel();

    void send(Notification notification, NotificationRecipientContact recipient);
}
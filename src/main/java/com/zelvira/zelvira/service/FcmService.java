package com.zelvira.zelvira.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class FcmService {

    public static final String NOTIFICATION_CHANNEL_ID = "zelixa_notifications";

    public void sendPushToToken(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("FCM token is empty, skipping push notification: {}", title);
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Skipping FCM push to token: {}", title);
            return;
        }

        try {
            Map<String, String> sanitizedData = sanitizeData(data);

            Message message = Message.builder()
                    .setToken(token.trim())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                                    .setSound("default")
                                    .setDefaultSound(true)
                                    .setDefaultVibrateTimings(true)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setIcon("/logo.png")
                                    .build())
                            .putHeader("Urgency", "high")
                            .build())
                    .putAllData(sanitizedData)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM push sent successfully to token. MessageId: {}, Title: {}", response, title);
        } catch (FirebaseMessagingException e) {
            log.error("FirebaseMessagingException sending FCM push to token (Title: {}): {} [ErrorCode: {}, MessagingErrorCode: {}]",
                    title, e.getMessage(), e.getErrorCode(), e.getMessagingErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error sending FCM push to token (Title: {}): {}", title, e.getMessage(), e);
        }
    }

    public void sendPushToTopic(String topic, String title, String body, Map<String, String> data) {
        if (topic == null || topic.trim().isEmpty()) {
            log.warn("FCM topic is empty, skipping push notification: {}", title);
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Skipping FCM push to topic {}: {}", topic, title);
            return;
        }

        try {
            Map<String, String> sanitizedData = sanitizeData(data);

            Message message = Message.builder()
                    .setTopic(topic.trim())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                                    .setSound("default")
                                    .setDefaultSound(true)
                                    .setDefaultVibrateTimings(true)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setIcon("/logo.png")
                                    .build())
                            .putHeader("Urgency", "high")
                            .build())
                    .putAllData(sanitizedData)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM push sent successfully to topic: {}. MessageId: {}, Title: {}", topic, response, title);
        } catch (FirebaseMessagingException e) {
            log.error("FirebaseMessagingException sending FCM push to topic {} (Title: {}): {} [ErrorCode: {}, MessagingErrorCode: {}]",
                    topic, title, e.getMessage(), e.getErrorCode(), e.getMessagingErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error sending FCM push to topic {} (Title: {}): {}", topic, title, e.getMessage(), e);
        }
    }

    private Map<String, String> sanitizeData(Map<String, String> data) {
        Map<String, String> sanitized = new HashMap<>();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sanitized.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return sanitized;
    }
}

package com.zelvira.zelvira.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.entity.NotificationHistory;
import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.NotificationHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private final FcmService fcmService;
    private final NotificationHistoryRepository historyRepository;

    public NotificationService(FcmService fcmService, NotificationHistoryRepository historyRepository) {
        this.fcmService = fcmService;
        this.historyRepository = historyRepository;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Notify a specific user and save in history.
     */
    public void notifyUser(User user, String title, String body, String type, Map<String, String> data) {
        if (user == null) {
            log.warn("Cannot notify null user: {}", title);
            return;
        }

        if (data == null)
            data = new HashMap<>();

        String jsonPayload = "";
        try {
            jsonPayload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            jsonPayload = data.toString();
        }

        // Save to history
        NotificationHistory history = NotificationHistory.builder()
                .userId(user.getId())
                .title(title)
                .body(body)
                .type(type)
                .isRead(false)
                .dataPayload(jsonPayload)
                .build();
        historyRepository.save(history);

        // Send FCM push
        if (user.getFcmToken() != null && !user.getFcmToken().trim().isEmpty()) {
            fcmService.sendPushToToken(user.getFcmToken(), title, body, data);
        } else {
            log.info("User {} ({}) has no FCM token, notification saved to in-app history only.", user.getId(),
                    user.getEmail());
        }
    }

    /**
     * Notify all users via a topic (e.g. "news" or "all") and save a global history
     * entry.
     */
    public void notifyTopic(String topic, String title, String body, String type, Map<String, String> data) {
        if (data == null)
            data = new HashMap<>();

        String jsonPayload = "";
        try {
            jsonPayload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            jsonPayload = data.toString();
        }

        // Save global history (userId = null)
        NotificationHistory history = NotificationHistory.builder()
                .userId(null) // Global/Topic notification
                .title(title)
                .body(body)
                .type(type)
                .isRead(false)
                .dataPayload(jsonPayload)
                .build();
        historyRepository.save(history);

        // Send FCM push to topic
        fcmService.sendPushToTopic(topic, title, body, data);
    }

    /**
     * Push notification: Menunggu Pembayaran (Order Placed / Pending Payment)
     */
    public void notifyOrderPending(Order order, User user) {
        if (order == null || user == null)
            return;

        String formattedAmount = formatCurrency(order.getTotalAmount());
        String title = "Menunggu Pembayaran ⏳";
        String body = "Pesanan #" + order.getOrderNumber() + " telah dibuat. Silakan selesaikan pembayaran sebesar "
                + formattedAmount + ".";

        Map<String, String> data = new HashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNumber", order.getOrderNumber());
        data.put("status", "PENDING");
        data.put("type", "ORDER");
        data.put("click_action", "ORDER_DETAIL");

        notifyUser(user, title, body, "ORDER", data);
    }

    /**
     * Push notification: Berhasil Bayar (Payment Success)
     */
    public void notifyPaymentSuccess(Order order, User user) {
        if (order == null || user == null)
            return;

        String title = "Pembayaran Berhasil! 🎉";
        String body = "Pembayaran untuk pesanan #" + order.getOrderNumber()
                + " telah kami terima. Pesanan Anda segera disiapkan!";

        Map<String, String> data = new HashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNumber", order.getOrderNumber());
        data.put("status", "PROCESSING");
        data.put("type", "ORDER");
        data.put("click_action", "ORDER_DETAIL");

        notifyUser(user, title, body, "ORDER", data);
    }

    /**
     * Push notification: Orderan Berubah Status (General Status Change)
     */
    public void notifyOrderStatusChanged(Order order, User user, String newStatus) {
        if (order == null || user == null || newStatus == null)
            return;

        String title;
        String body;

        switch (newStatus.toUpperCase()) {
            case "PENDING":
                notifyOrderPending(order, user);
                return;
            case "PROCESSING":
            case "PAID":
                notifyPaymentSuccess(order, user);
                return;
            case "DELIVERING":
            case "SHIPPED":
                title = "Pesanan Sedang Dikirim 🚚";
                body = "Pesanan #" + order.getOrderNumber() + " sedang dalam proses pengiriman.";
                if (order.getTrackingNumber() != null && !order.getTrackingNumber().trim().isEmpty()) {
                    body += " No. Resi: " + order.getTrackingNumber();
                }
                break;
            case "COMPLETED":
            case "DELIVERED":
                title = "Pesanan Telah Sampai 📦";
                body = "Pesanan #" + order.getOrderNumber()
                        + " telah sampai di tujuan. Terima kasih telah berbelanja di Zelvira!";
                break;
            case "CANCELLED":
            case "CANCELED":
                title = "Pesanan Dibatalkan ❌";
                body = "Pesanan #" + order.getOrderNumber() + " telah dibatalkan.";
                break;
            default:
                title = "Status Pesanan Diperbarui 📋";
                body = "Status pesanan #" + order.getOrderNumber() + " saat ini adalah " + newStatus + ".";
                break;
        }

        Map<String, String> data = new HashMap<>();
        data.put("orderId", String.valueOf(order.getId()));
        data.put("orderNumber", order.getOrderNumber());
        data.put("status", newStatus);
        data.put("type", "ORDER");
        data.put("click_action", "ORDER_DETAIL");
        if (order.getTrackingNumber() != null) {
            data.put("trackingNumber", order.getTrackingNumber());
        }

        notifyUser(user, title, body, "ORDER", data);
    }

    /**
     * Push notification: News untuk semua pengguna (Broadcast News)
     */
    public void notifyBroadcastNews(String title, String content, Long newsId, String slug) {
        if (title == null || title.trim().isEmpty())
            return;

        String body = content != null ? content.replaceAll("\\<.*?\\>", "")
                : "Ada berita dan promo terbaru di Zelvira. Buka aplikasi untuk melihat selengkapnya!";
        if (body.length() > 140) {
            body = body.substring(0, 137) + "...";
        }

        Map<String, String> data = new HashMap<>();
        if (newsId != null)
            data.put("newsId", String.valueOf(newsId));
        if (slug != null)
            data.put("slug", slug);
        data.put("type", "NEWS");
        data.put("click_action", "NEWS_DETAIL");

        // Broadcast to "all" and "news" topics
        notifyTopic("all", "📢 " + title, body, "NEWS", data);
        fcmService.sendPushToTopic("news", "📢 " + title, body, data);
    }

    public void notifyPersonalNews(User targetUser, String title, String content, Long newsId, String slug) {
        if (title == null || title.trim().isEmpty() || targetUser == null)
            return;

        String body = content != null ? content.replaceAll("\\<.*?\\>", "")
                : "Ada berita dan promo terbaru di Zelvira. Buka aplikasi untuk melihat selengkapnya!";
        if (body.length() > 140) {
            body = body.substring(0, 137) + "...";
        }

        Map<String, String> data = new HashMap<>();
        if (newsId != null)
            data.put("newsId", String.valueOf(newsId));
        if (slug != null)
            data.put("slug", slug);
        data.put("type", "NEWS");
        data.put("click_action", "NEWS_DETAIL");

        notifyUser(targetUser, "📢 " + title, body, "NEWS", data);
    }

    private String formatCurrency(Double amount) {
        if (amount == null)
            return "Rp 0";
        try {
            Locale indo = new Locale("id", "ID");
            NumberFormat format = NumberFormat.getCurrencyInstance(indo);
            format.setMaximumFractionDigits(0);
            return format.format(amount);
        } catch (Exception e) {
            return "Rp " + String.format("%,.0f", amount);
        }
    }
}

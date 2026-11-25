package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.entity.NotificationHistory;
import com.zelvira.zelvira.repository.NotificationHistoryRepository;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationHistoryRepository historyRepository,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationHistory>>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<NotificationHistory> notifications = historyRepository.findUserAndGlobalNotifications(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched", notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long count = historyRepository.countByUserIdAndIsReadFalse(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NotificationHistory history = historyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notifikasi tidak ditemukan"));

        if (history.getUser() != null && !history.getUser().getId().equals(userDetails.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak");
        }

        history.setIsRead(true);
        historyRepository.save(history);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<NotificationHistory> unread = historyRepository.findByUserIdAndIsReadFalse(userDetails.getId());
        unread.forEach(n -> n.setIsRead(true));
        historyRepository.saveAll(unread);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        historyRepository.deleteByUserId(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification history cleared", null));
    }

    @PostMapping("/push")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendPushNotification(
            @RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String body = payload.get("body");
        String type = payload.get("type");
        String userIdStr = payload.get("userId");
        String topic = payload.get("topic");

        if (userIdStr != null && !userIdStr.isEmpty()) {
            Long userId = Long.parseLong(userIdStr);
            com.zelvira.zelvira.entity.User user = userRepository.findById(userId).orElseThrow();
            notificationService.notifyUser(user, title, body, type, new HashMap<>());
        } else if (topic != null && !topic.isEmpty()) {
            notificationService.notifyTopic(topic, title, body, type, new HashMap<>());
        } else {
            notificationService.notifyTopic("all", title, body, type, new HashMap<>());
        }

        return ResponseEntity.ok(ApiResponse.success("Notification pushed", null));
    }
}

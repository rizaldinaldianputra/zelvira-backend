package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ChatMessagePayload;
import com.zelvira.zelvira.entity.ChatMessage;
import com.zelvira.zelvira.entity.ChatRoom;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.ChatMessageRepository;
import com.zelvira.zelvira.repository.ChatRoomRepository;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.service.AiChatService;
import com.zelvira.zelvira.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class WsChatController {

    private static final Logger log = LoggerFactory.getLogger(WsChatController.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiChatService aiChatService;

    // Rate Limiting Cache: SenderId -> List of message epoch timestamps (millis)
    private final Map<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    // Cooldown expiry: SenderId -> Expiry Epoch millis
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();

    private static final int MAX_MESSAGES_WINDOW = 5; // max 5 messages
    private static final long TIME_WINDOW_MS = 5000; // per 5 seconds
    private static final long COOLDOWN_DURATION_MS = 15000; // 15 seconds penalty

    public WsChatController(ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate,
            AiChatService aiChatService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
        this.aiChatService = aiChatService;
    }

    @MessageMapping("/chat/{roomId}/sendMessage")
    public void sendMessage(@DestinationVariable String roomId, @Payload ChatMessagePayload chatMessagePayload) {
        String senderId = chatMessagePayload.getSenderId();
        String destination = "/topic/room/" + roomId;

        if (chatMessagePayload.getType() == null) {
            chatMessagePayload.setType(ChatMessagePayload.MessageType.CHAT);
        }

        if (chatMessagePayload.getType() == ChatMessagePayload.MessageType.MODE_CHANGE) {
            if (chatMessagePayload.getChatMode() != null) {
                ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                        .orElseGet(() -> ChatRoom.builder().roomId(roomId).chatMode("AI").build());
                room.setChatMode(chatMessagePayload.getChatMode());
                chatRoomRepository.save(room);
            }
            messagingTemplate.convertAndSend(destination, chatMessagePayload);
            return;
        }

        if (chatMessagePayload.getType() == ChatMessagePayload.MessageType.CHAT) {
            long now = System.currentTimeMillis();

            // 1. Anti-Spam Check
            if (isRateLimited(senderId, now)) {
                long expireAt = cooldownMap.getOrDefault(senderId, now + COOLDOWN_DURATION_MS);
                long retryAfterSec = Math.max(1, (expireAt - now) / 1000);

                ChatMessagePayload cooldownNotice = ChatMessagePayload.builder()
                        .roomId(roomId)
                        .senderId("SYSTEM")
                        .senderRole("SYSTEM")
                        .content("Terlalu banyak mengirim pesan (Spam terdeteksi). Harap tunggu " + retryAfterSec
                                + " detik.")
                        .type(ChatMessagePayload.MessageType.ERROR_COOLDOWN)
                        .retryAfterSeconds(retryAfterSec)
                        .build();

                // Send rejection notice specifically or to room
                messagingTemplate.convertAndSend(destination, cooldownNotice);
                return;
            }

            // 2. Find or create room
            ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                    .orElseGet(() -> {
                        ChatRoom newRoom = ChatRoom.builder()
                                .roomId(roomId)
                                .chatMode("AI")
                                .build();
                        return chatRoomRepository.save(newRoom);
                    });

            if (room.getChatMode() == null) {
                room.setChatMode("AI");
            }

            String senderRole = chatMessagePayload.getSenderRole();
            if (senderRole == null || senderRole.isBlank()) {
                senderRole = "CUSTOMER";
            }
            boolean isAdminOrCs = "ADMIN".equalsIgnoreCase(senderRole)
                    || "CS".equalsIgnoreCase(senderRole)
                    || "AI".equalsIgnoreCase(senderRole)
                    || "SYSTEM".equalsIgnoreCase(senderRole);

            // Associate userId if room is missing it
            if (room.getUserId() == null) {
                User user = resolveCustomerUser(roomId);
                if (user == null && senderId != null && !isAdminOrCs) {
                    try {
                        Long uid = Long.parseLong(senderId);
                        user = userRepository.findById(uid).orElse(null);
                    } catch (NumberFormatException ignored) {
                    }
                    if (user == null && senderId.contains("@")) {
                        user = userRepository.findByEmail(senderId).orElse(null);
                    }
                }
                if (user != null) {
                    room.setUserId(user.getId());
                    room = chatRoomRepository.save(room);
                }
            }

            // If human Admin/CS replies directly, switch room mode to LIVE_CS
            if (("ADMIN".equalsIgnoreCase(senderRole) || "CS".equalsIgnoreCase(senderRole))
                    && !"ZELVIRA_AI".equalsIgnoreCase(senderId)) {
                if (!"LIVE_CS".equalsIgnoreCase(room.getChatMode())) {
                    room.setChatMode("LIVE_CS");
                    room = chatRoomRepository.save(room);
                }
            }

            // 3. Save message
            ChatMessage message = ChatMessage.builder()
                    .chatRoom(room)
                    .senderId(senderId)
                    .senderRole(senderRole)
                    .content(chatMessagePayload.getContent())
                    .attachmentUrl(chatMessagePayload.getAttachmentUrl())
                    .build();
            ChatMessage savedMessage = chatMessageRepository.save(message);

            // Populate generated metadata on outgoing payload
            chatMessagePayload.setId(savedMessage.getId());
            chatMessagePayload.setSenderRole(senderRole);
            chatMessagePayload.setChatMode(room.getChatMode() != null ? room.getChatMode() : "AI");
            chatMessagePayload.setCreatedAt(savedMessage.getCreatedAt() != null ? savedMessage.getCreatedAt().toString()
                    : java.time.LocalDateTime.now().toString());

            // 4. Trigger FCM Notification
            triggerNotification(roomId, chatMessagePayload, room.getChatMode());

            // Broadcast valid customer/admin message to subscribers of the room first
            messagingTemplate.convertAndSend(destination, chatMessagePayload);

            // 5. AI-First Handling for Customer Messages (when room is in AI mode)
            if (!isAdminOrCs) {
                String currentMode = room.getChatMode() != null ? room.getChatMode() : "AI";
                String customerText = chatMessagePayload.getContent() != null ? chatMessagePayload.getContent().trim()
                        : "";

                // Allow customer to switch back to AI mode if they type "mode ai" / "kembali ke
                // ai"
                if ("LIVE_CS".equalsIgnoreCase(currentMode)
                        && (customerText.equalsIgnoreCase("mode ai")
                                || customerText.equalsIgnoreCase("kembali ke ai"))) {
                    room.setChatMode("AI");
                    chatRoomRepository.save(room);
                    currentMode = "AI";
                }

                if ("AI".equalsIgnoreCase(currentMode)) {
                    final Long roomDbId = room.getId();
                    final ChatRoom initialRoom = room;
                    Long resolvedUserId = room.getUserId();
                    if (resolvedUserId == null && senderId != null) {
                        try {
                            resolvedUserId = Long.parseLong(senderId);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    final Long finalUserId = resolvedUserId;
                    final String msgContent = chatMessagePayload.getContent();
                    final String msgAttachment = chatMessagePayload.getAttachmentUrl();

                    // Send immediate TYPING indicator to room so user sees "AI sedang menulis..."
                    try {
                        ChatMessagePayload typingNotice = ChatMessagePayload.builder()
                                .roomId(roomId)
                                .senderId("ZELVIRA_AI")
                                .senderRole("AI")
                                .type(ChatMessagePayload.MessageType.TYPING)
                                .content("AI sedang menulis...")
                                .chatMode("AI")
                                .build();
                        messagingTemplate.convertAndSend(destination, typingNotice);
                    } catch (Exception ex) {
                        log.warn("[WsChatController] Could not broadcast TYPING status: {}", ex.getMessage());
                    }

                    CompletableFuture.runAsync(() -> {
                        try {
                            AiChatService.AiChatResult aiResult = aiChatService.processCustomerMessage(
                                    msgContent,
                                    msgAttachment,
                                    roomId,
                                    finalUserId);

                            ChatRoom latestRoom = chatRoomRepository.findById(roomDbId).orElse(initialRoom);
                            String effectiveMode = latestRoom.getChatMode() != null ? latestRoom.getChatMode() : "AI";

                            if (aiResult.isHandoffToLiveCs()) {
                                effectiveMode = "LIVE_CS";
                                latestRoom.setChatMode("LIVE_CS");
                                chatRoomRepository.save(latestRoom);

                                // Alert admins of Live CS handoff request
                                Map<String, String> data = new HashMap<>();
                                data.put("type", "CHAT");
                                data.put("roomId", roomId);
                                data.put("route", "/admin/chat/" + roomId);
                                notificationService.notifyTopic(
                                        "admin",
                                        "Permintaan Live CS Baru 👩‍💼",
                                        "Pelanggan (" + roomId + ") meminta bantuan Live CS: "
                                                + (msgContent != null ? msgContent : ""),
                                        "CHAT",
                                        data);
                            }

                            ChatMessage aiMessage = ChatMessage.builder()
                                    .chatRoom(latestRoom)
                                    .senderId("ZELVIRA_AI")
                                    .senderRole("AI")
                                    .content(aiResult.getReply())
                                    .build();
                            ChatMessage savedAiMsg = chatMessageRepository.save(aiMessage);

                            ChatMessagePayload aiPayload = ChatMessagePayload.builder()
                                    .id(savedAiMsg.getId())
                                    .roomId(roomId)
                                    .senderId("ZELVIRA_AI")
                                    .senderRole("AI")
                                    .content(aiResult.getReply())
                                    .type(ChatMessagePayload.MessageType.CHAT)
                                    .intent(aiResult.getIntent())
                                    .suggestLiveChat(aiResult.isSuggestLiveChat())
                                    .chatMode(effectiveMode)
                                    .createdAt(savedAiMsg.getCreatedAt() != null ? savedAiMsg.getCreatedAt().toString()
                                            : java.time.LocalDateTime.now().toString())
                                    .build();

                            messagingTemplate.convertAndSend(destination, aiPayload);
                        } catch (Exception ex) {
                            log.error("[WsChatController] Failed to process AI reply for room {}: {}", roomId,
                                    ex.getMessage(), ex);
                            try {
                                String fallback = "Maaf Kak, asisten AI sedang mengalami sedikit kendala koneksi. Kakak bisa mencoba bertanya kembali atau klik tombol di bawah untuk terhubung langsung dengan Admin Live CS kami.";
                                ChatRoom latestRoom = chatRoomRepository.findById(roomDbId).orElse(initialRoom);
                                ChatMessage fallbackMsg = ChatMessage.builder()
                                        .chatRoom(latestRoom)
                                        .senderId("ZELVIRA_AI")
                                        .senderRole("AI")
                                        .content(fallback)
                                        .build();
                                ChatMessage savedFallback = chatMessageRepository.save(fallbackMsg);

                                ChatMessagePayload errorPayload = ChatMessagePayload.builder()
                                        .id(savedFallback.getId())
                                        .roomId(roomId)
                                        .senderId("ZELVIRA_AI")
                                        .senderRole("AI")
                                        .content(fallback)
                                        .type(ChatMessagePayload.MessageType.CHAT)
                                        .suggestLiveChat(true)
                                        .chatMode("AI")
                                        .createdAt(savedFallback.getCreatedAt() != null
                                                ? savedFallback.getCreatedAt().toString()
                                                : java.time.LocalDateTime.now().toString())
                                        .build();
                                messagingTemplate.convertAndSend(destination, errorPayload);
                            } catch (Exception sendErr) {
                                log.error("[WsChatController] Failed to send fallback message: {}",
                                        sendErr.getMessage());
                            }
                        }
                    });
                }
            }

            return;
        }

        // Broadcast non-CHAT message (JOIN / LEAVE) to subscribers of the room
        messagingTemplate.convertAndSend(destination, chatMessagePayload);
    }

    private boolean isRateLimited(String senderId, long now) {
        if (senderId == null || senderId.equalsIgnoreCase("ADMIN") || senderId.equalsIgnoreCase("CS")) {
            return false; // Don't throttle admins
        }

        // Check if currently penalized
        Long penaltyExpiry = cooldownMap.get(senderId);
        if (penaltyExpiry != null) {
            if (now < penaltyExpiry) {
                return true;
            } else {
                cooldownMap.remove(senderId);
                rateLimitMap.remove(senderId);
            }
        }

        List<Long> timestamps = rateLimitMap.computeIfAbsent(senderId,
                k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (timestamps) {
            // Remove timestamps older than window
            timestamps.removeIf(ts -> now - ts > TIME_WINDOW_MS);
            timestamps.add(now);

            if (timestamps.size() > MAX_MESSAGES_WINDOW) {
                // Trigger cooldown
                cooldownMap.put(senderId, now + COOLDOWN_DURATION_MS);
                return true;
            }
        }

        return false;
    }

    private void triggerNotification(String roomId, ChatMessagePayload chatMessagePayload, String currentChatMode) {
        try {
            String senderRole = chatMessagePayload.getSenderRole();
            String content = chatMessagePayload.getContent();
            if (content == null || content.trim().isEmpty()) {
                content = "[Lampiran Gambar]";
            }

            // Case A: Message sent by ADMIN to customer
            if ("ADMIN".equalsIgnoreCase(senderRole) || "CS".equalsIgnoreCase(senderRole)) {
                User targetUser = resolveCustomerUser(roomId);
                if (targetUser != null) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "CHAT");
                    data.put("roomId", roomId);
                    data.put("route", "/support/chat");
                    data.put("click_action", "CHAT_SUPPORT");

                    notificationService.notifyUser(
                            targetUser,
                            "Pesan Baru dari Customer Service 💬",
                            content,
                            "CHAT",
                            data);
                }
            } else if (!"AI".equalsIgnoreCase(senderRole) && !"SYSTEM".equalsIgnoreCase(senderRole)) {
                // Case B: Message sent by CUSTOMER when in LIVE_CS mode
                if ("LIVE_CS".equalsIgnoreCase(currentChatMode)) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "CHAT");
                    data.put("roomId", roomId);
                    data.put("route", "/admin/chat/" + roomId);

                    notificationService.notifyTopic(
                            "admin",
                            "Pesan Baru dari Pelanggan (" + roomId + ") 💬",
                            content,
                            "CHAT",
                            data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send chat FCM notification: {}", e.getMessage());
        }
    }

    private User resolveCustomerUser(String roomId) {
        if (roomId == null)
            return null;

        // Pattern 1: user_123
        if (roomId.startsWith("user_")) {
            try {
                Long userId = Long.parseLong(roomId.replace("user_", ""));
                return userRepository.findById(userId).orElse(null);
            } catch (Exception ignored) {
            }
        }

        // Pattern 2: direct email or phone
        if (roomId.contains("@")) {
            return userRepository.findByEmail(roomId).orElse(null);
        }

        return null;
    }

    @MessageMapping("/chat/{roomId}/addUser")
    @SendTo("/topic/room/{roomId}")
    public ChatMessagePayload addUser(@DestinationVariable String roomId,
            @Payload ChatMessagePayload chatMessagePayload,
            SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("username", chatMessagePayload.getSenderId());
            headerAccessor.getSessionAttributes().put("roomId", roomId);
        }
        chatRoomRepository.findByRoomId(roomId).ifPresent(room -> {
            chatMessagePayload.setChatMode(room.getChatMode() != null ? room.getChatMode() : "AI");
        });
        return chatMessagePayload;
    }
}

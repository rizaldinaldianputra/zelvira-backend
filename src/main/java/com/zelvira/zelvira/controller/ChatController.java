package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.ChatMessagePayload;
import com.zelvira.zelvira.dto.ChatRoomDto;
import com.zelvira.zelvira.entity.ChatMessage;
import com.zelvira.zelvira.entity.ChatRoom;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.ChatMessageRepository;
import com.zelvira.zelvira.repository.ChatRoomRepository;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.service.AiChatService;
import com.zelvira.zelvira.service.WhatsAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final WhatsAppService whatsappService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final AiChatService aiChatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(WhatsAppService whatsappService,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            AiChatService aiChatService,
            SimpMessagingTemplate messagingTemplate) {
        this.whatsappService = whatsappService;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.aiChatService = aiChatService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> sendMessage(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        String message = request.get("message");

        if (phoneNumber == null || message == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Phone number and message are required"));
        }

        whatsappService.sendText(phoneNumber, message);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", null));
    }

    @GetMapping("/{roomId}/history")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getChatHistory(@PathVariable String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .map(room -> {
                    List<ChatMessage> messages = chatMessageRepository
                            .findByChatRoomIdOrderByCreatedAtAsc(room.getId());
                    return ResponseEntity.ok(ApiResponse.success("Chat history retrieved", messages));
                })
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("No chat history", Collections.emptyList())));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> getChatRooms() {
        List<ChatRoom> rooms = chatRoomRepository.findAllByOrderByUpdatedAtDesc();
        List<ChatRoomDto> dtos = rooms.stream().map(room -> {
            String roomId = room.getRoomId();
            String customerName = "Pelanggan (" + roomId + ")";
            String customerEmail = "";
            String customerAvatar = null;

            if (roomId.startsWith("user_")) {
                try {
                    Long userId = Long.parseLong(roomId.replace("user_", ""));
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        customerName = user.getFullName() != null ? user.getFullName() : user.getEmail();
                        customerEmail = user.getEmail();
                        customerAvatar = user.getProfilePicture();
                    }
                } catch (Exception ignored) {
                }
            } else if (roomId.contains("@")) {
                Optional<User> userOpt = userRepository.findByEmail(roomId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    customerName = user.getFullName() != null ? user.getFullName() : user.getEmail();
                    customerEmail = user.getEmail();
                    customerAvatar = user.getProfilePicture();
                }
            }

            Optional<ChatMessage> lastMsgOpt = chatMessageRepository
                    .findTopByChatRoomIdOrderByCreatedAtDesc(room.getId());
            String lastMessage = lastMsgOpt.map(m -> m.getContent() != null ? m.getContent() : "[Lampiran]")
                    .orElse("Belum ada pesan");

            return ChatRoomDto.builder()
                    .id(room.getId())
                    .roomId(room.getRoomId())
                    .customerName(customerName)
                    .customerEmail(customerEmail)
                    .customerAvatar(customerAvatar)
                    .lastMessage(lastMessage)
                    .chatMode(room.getChatMode() != null ? room.getChatMode() : "AI")
                    .updatedAt(room.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Chat rooms retrieved", dtos));
    }

    @GetMapping("/{roomId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoomStatus(@PathVariable String roomId) {
        String mode = chatRoomRepository.findByRoomId(roomId)
                .map(r -> r.getChatMode() != null ? r.getChatMode() : "AI")
                .orElse("AI");
        return ResponseEntity.ok(ApiResponse.success("Chat room status", Map.of(
                "roomId", roomId,
                "chatMode", mode)));
    }

    @PostMapping("/{roomId}/mode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchChatMode(
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {
        String targetMode = request.getOrDefault("mode", "LIVE_CS").trim().toUpperCase();
        if (!"AI".equals(targetMode) && !"LIVE_CS".equals(targetMode)) {
            targetMode = "LIVE_CS";
        }

        final String finalMode = targetMode;
        ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                .orElseGet(() -> ChatRoom.builder().roomId(roomId).chatMode(finalMode).build());
        room.setChatMode(finalMode);
        chatRoomRepository.save(room);

        String noticeText = "LIVE_CS".equals(finalMode)
                ? "👩‍💼 **Live Chat Customer Service Aktif**\nRuang obrolan ini sekarang terhubung langsung dengan Admin Customer Service Zelvira. Silakan tulis pesan atau kendala Kakak, tim kami akan segera membalas!"
                : "🤖 **Zelvira AI Assistant Aktif**\nRuang obrolan kembali ditangani oleh Asisten AI Zelvira. Ada produk, promo, atau informasi yang ingin Kakak cari?";

        ChatMessage noticeMsg = ChatMessage.builder()
                .chatRoom(room)
                .senderId("ZELVIRA_AI")
                .senderRole("AI")
                .content(noticeText)
                .build();
        ChatMessage savedNotice = chatMessageRepository.save(noticeMsg);

        ChatMessagePayload broadcastPayload = ChatMessagePayload.builder()
                .id(savedNotice.getId())
                .roomId(roomId)
                .senderId("ZELVIRA_AI")
                .senderRole("AI")
                .content(noticeText)
                .type(ChatMessagePayload.MessageType.MODE_CHANGE)
                .chatMode(finalMode)
                .suggestLiveChat(false)
                .createdAt(savedNotice.getCreatedAt() != null ? savedNotice.getCreatedAt().toString() : java.time.LocalDateTime.now().toString())
                .build();
        messagingTemplate.convertAndSend("/topic/room/" + roomId, broadcastPayload);

        return ResponseEntity.ok(ApiResponse.success("Chat mode updated", Map.of(
                "roomId", roomId,
                "chatMode", finalMode,
                "message", noticeText)));
    }

    @PostMapping({"/ai", "/ask"})
    public ResponseEntity<ApiResponse<AiChatService.AiChatResult>> askAi(@RequestBody Map<String, Object> request) {
        String message = request.get("message") != null ? String.valueOf(request.get("message")) : "";
        String imageUrl = request.get("imageUrl") != null ? String.valueOf(request.get("imageUrl")) : null;
        String roomId = request.get("roomId") != null ? String.valueOf(request.get("roomId")) : null;
        Long userId = null;
        if (request.get("userId") != null) {
            try {
                userId = Long.parseLong(String.valueOf(request.get("userId")));
            } catch (NumberFormatException ignored) {
            }
        }

        AiChatService.AiChatResult result = aiChatService.processCustomerMessage(message, imageUrl, roomId, userId);
        return ResponseEntity.ok(ApiResponse.success("AI response generated", result));
    }
}

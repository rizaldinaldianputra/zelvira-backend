package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ChatRoom chatRoom;

    // Sender's identifier (could be "ADMIN" or a specific user ID)
    @Column(nullable = false)
    private String senderId;

    // Role of sender (e.g., "ADMIN", "CS", "CUSTOMER")
    private String senderRole;

    @Column(columnDefinition = "TEXT")
    private String content;

    // URL to the attachment if uploaded
    private String attachmentUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public ChatMessage(Long id, ChatRoom chatRoom, String senderId, String senderRole, String content,
            String attachmentUrl, LocalDateTime createdAt) {
        this.id = id;
        this.chatRoom = chatRoom;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.attachmentUrl = attachmentUrl;
        this.createdAt = createdAt;
    }

    public static ChatMessageBuilder builder() {
        return new ChatMessageBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public void setChatRoom(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class ChatMessageBuilder {
        private Long id;
        private ChatRoom chatRoom;
        private String senderId;
        private String senderRole;
        private String content;
        private String attachmentUrl;
        private LocalDateTime createdAt;

        ChatMessageBuilder() {
        }

        public ChatMessageBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ChatMessageBuilder chatRoom(ChatRoom chatRoom) {
            this.chatRoom = chatRoom;
            return this;
        }

        public ChatMessageBuilder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public ChatMessageBuilder senderRole(String senderRole) {
            this.senderRole = senderRole;
            return this;
        }

        public ChatMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        public ChatMessageBuilder attachmentUrl(String attachmentUrl) {
            this.attachmentUrl = attachmentUrl;
            return this;
        }

        public ChatMessageBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ChatMessage build() {
            return new ChatMessage(id, chatRoom, senderId, senderRole, content, attachmentUrl, createdAt);
        }
    }

}
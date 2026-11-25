package com.zelvira.zelvira.dto;

import java.time.LocalDateTime;

public class ChatRoomDto {
    private Long id;
    private String roomId;
    private String customerName;
    private String customerEmail;
    private String customerAvatar;
    private String lastMessage;
    private String chatMode;
    private LocalDateTime updatedAt;

    public ChatRoomDto() {
    }

    public ChatRoomDto(Long id, String roomId, String customerName, String customerEmail, String customerAvatar,
            String lastMessage, LocalDateTime updatedAt) {
        this.id = id;
        this.roomId = roomId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerAvatar = customerAvatar;
        this.lastMessage = lastMessage;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String roomId;
        private String customerName;
        private String customerEmail;
        private String customerAvatar;
        private String lastMessage;
        private String chatMode;
        private LocalDateTime updatedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder roomId(String roomId) {
            this.roomId = roomId;
            return this;
        }

        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public Builder customerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
            return this;
        }

        public Builder customerAvatar(String customerAvatar) {
            this.customerAvatar = customerAvatar;
            return this;
        }

        public Builder lastMessage(String lastMessage) {
            this.lastMessage = lastMessage;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder chatMode(String chatMode) {
            this.chatMode = chatMode;
            return this;
        }

        public ChatRoomDto build() {
            ChatRoomDto dto = new ChatRoomDto(id, roomId, customerName, customerEmail, customerAvatar, lastMessage, updatedAt);
            dto.setChatMode(chatMode);
            return dto;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerAvatar() {
        return customerAvatar;
    }

    public void setCustomerAvatar(String customerAvatar) {
        this.customerAvatar = customerAvatar;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getChatMode() {
        return chatMode;
    }

    public void setChatMode(String chatMode) {
        this.chatMode = chatMode;
    }
}

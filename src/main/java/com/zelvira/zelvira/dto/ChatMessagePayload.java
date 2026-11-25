package com.zelvira.zelvira.dto;

public class ChatMessagePayload {
    private Long id;
    private String roomId;
    private String senderId;
    private String senderRole;
    private String content;
    private String attachmentUrl;
    private MessageType type;
    private Long retryAfterSeconds;
    private String intent;
    private Boolean suggestLiveChat;
    private String chatMode;
    private String createdAt;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        ERROR_COOLDOWN,
        MODE_CHANGE,
        TYPING
    }

    public ChatMessagePayload() {
    }

    public ChatMessagePayload(Long id, String roomId, String senderId, String senderRole, String content,
            String attachmentUrl, MessageType type, Long retryAfterSeconds, String intent, Boolean suggestLiveChat,
            String chatMode, String createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.attachmentUrl = attachmentUrl;
        this.type = type;
        this.retryAfterSeconds = retryAfterSeconds;
        this.intent = intent;
        this.suggestLiveChat = suggestLiveChat;
        this.chatMode = chatMode;
        this.createdAt = createdAt;
    }

    public ChatMessagePayload(String roomId, String senderId, String senderRole, String content, String attachmentUrl,
            MessageType type, Long retryAfterSeconds) {
        this(null, roomId, senderId, senderRole, content, attachmentUrl, type, retryAfterSeconds, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String roomId;
        private String senderId;
        private String senderRole;
        private String content;
        private String attachmentUrl;
        private MessageType type;
        private Long retryAfterSeconds;
        private String intent;
        private Boolean suggestLiveChat;
        private String chatMode;
        private String createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder roomId(String roomId) {
            this.roomId = roomId;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder senderRole(String senderRole) {
            this.senderRole = senderRole;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder attachmentUrl(String attachmentUrl) {
            this.attachmentUrl = attachmentUrl;
            return this;
        }

        public Builder type(MessageType type) {
            this.type = type;
            return this;
        }

        public Builder retryAfterSeconds(Long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public Builder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public Builder suggestLiveChat(Boolean suggestLiveChat) {
            this.suggestLiveChat = suggestLiveChat;
            return this;
        }

        public Builder chatMode(String chatMode) {
            this.chatMode = chatMode;
            return this;
        }

        public Builder createdAt(String createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ChatMessagePayload build() {
            return new ChatMessagePayload(id, roomId, senderId, senderRole, content, attachmentUrl, type,
                    retryAfterSeconds, intent, suggestLiveChat, chatMode, createdAt);
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

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Boolean getSuggestLiveChat() {
        return suggestLiveChat;
    }

    public void setSuggestLiveChat(Boolean suggestLiveChat) {
        this.suggestLiveChat = suggestLiveChat;
    }

    public String getChatMode() {
        return chatMode;
    }

    public void setChatMode(String chatMode) {
        this.chatMode = chatMode;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}


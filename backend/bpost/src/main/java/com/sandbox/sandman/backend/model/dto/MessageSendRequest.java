package com.sandbox.sandman.backend.model.dto;

import lombok.Data;

@Data
public class MessageSendRequest {
    private Long conversationId;
    private Long recipientId; // used when conversationId is null (auto-create with recipient)
    private String content;
    private String imageUrl;
}

package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private String imageUrl;
    private ZonedDateTime createdAt;
    private ZonedDateTime readAt;
}

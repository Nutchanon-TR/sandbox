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
public class ConversationDto {
    private Long id;
    private UserSummaryDto otherUser;
    private ZonedDateTime lastMessageAt;
    private long unreadCount;
    private MessageDto lastMessage;
}

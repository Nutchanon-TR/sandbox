package com.sandbox.sandman.backend.model.dto.ChatDto;

import lombok.Data;
import java.time.ZonedDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long roomId;
    private Long senderId;          // NULL when AI
    private String senderName;      // display_name or ai_name
    private Boolean isAi;
    private String content;
    private ZonedDateTime createdAt;
}

package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChatDto {
    private Long id;
    private Long roomId;
    private Long senderId;          // NULL when AI
    private String senderName;      // display_name or ai_name
    private Boolean isAi;
    private String content;
    private ZonedDateTime createdAt;
    private List<ChatAttachmentDto> attachments = new ArrayList<>();
}

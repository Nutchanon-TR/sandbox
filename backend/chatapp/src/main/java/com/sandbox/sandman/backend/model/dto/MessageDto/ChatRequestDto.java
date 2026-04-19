package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;

@Data
public class ChatRequestDto {
    private Long roomId;
    private Long senderId;
    private String message;
}

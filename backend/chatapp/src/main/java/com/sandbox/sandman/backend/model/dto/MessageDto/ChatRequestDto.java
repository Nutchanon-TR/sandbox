package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;

@Data
public class ChatRequestDto {
    private Long roomId;
    private String message;
    // senderId removed: caller identity is resolved server-side from JWT (see CurrentUser).
}

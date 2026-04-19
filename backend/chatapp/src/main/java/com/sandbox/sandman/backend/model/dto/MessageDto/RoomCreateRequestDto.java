package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;

@Data
public class RoomCreateRequestDto {
    private String name;
    private String systemPrompt;
}

package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;

import java.util.List;

@Data
public class RoomCreateRequestDto {
    private String name;
    private Boolean isGroup = false;
    private String aiModel;
    private String systemPrompt;
    private List<Long> memberIds;  // สำหรับ group room
}

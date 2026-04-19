package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
public class RoomDto {
    private Long id;
    private String name;
    private String aiAvatarUrl;
    private ZonedDateTime createdAt;
}

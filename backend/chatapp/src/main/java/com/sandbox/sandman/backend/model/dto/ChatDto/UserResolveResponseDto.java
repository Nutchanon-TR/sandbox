package com.sandbox.sandman.backend.model.dto.ChatDto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResolveResponseDto {
    private Long userId;
    private Long roomId;
}

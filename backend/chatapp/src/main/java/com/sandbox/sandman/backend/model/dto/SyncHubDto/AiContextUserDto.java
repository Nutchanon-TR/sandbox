package com.sandbox.sandman.backend.model.dto.SyncHubDto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiContextUserDto {
    private Long aiId;
    private String aiName;
    private String avatarUrl;
    private Long userId;
    private String userDisplayName;
    private Long roomId;
}

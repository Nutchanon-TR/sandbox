package com.sandbox.sandman.backend.model.dto.SyncHubDto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiDetailDto {
    private Long aiId;
    private String aiName;
    private String avatarUrl;
    private String posterUrl;
    private String role;
    private String character;
    private String biography;
    private String rule;
    private Long likeCount;
    private Long friendCount;
    private Long commentCount;
}

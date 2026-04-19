package com.sandbox.sandman.backend.model.dto.SyncHubDto;

import lombok.Data;

@Data
public class CommentCreateRequestDto {
    private Long userId;
    private String content;
}

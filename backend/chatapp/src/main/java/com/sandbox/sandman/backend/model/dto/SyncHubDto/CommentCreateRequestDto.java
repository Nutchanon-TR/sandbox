package com.sandbox.sandman.backend.model.dto.SyncHubDto;

import lombok.Data;

@Data
public class CommentCreateRequestDto {
    private String content;
    private Integer star;
    // userId removed: caller identity is resolved server-side from JWT (see CurrentUser).
}

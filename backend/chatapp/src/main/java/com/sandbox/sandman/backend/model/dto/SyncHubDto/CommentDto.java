package com.sandbox.sandman.backend.model.dto.SyncHubDto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
public class CommentDto {
    private Long id;
    private Long userId;
    private String userDisplayName;
    private Long aiId;
    private String content;
    private Integer star;
    private Long likeCount;
    private ZonedDateTime createdAt;
}

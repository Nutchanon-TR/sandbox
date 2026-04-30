package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private Long id;
    private UserSummaryDto author;
    private String content;
    private List<String> imageUrls;
    private String visibility;
    private ZonedDateTime createdAt;
    private ZonedDateTime editedAt;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
}

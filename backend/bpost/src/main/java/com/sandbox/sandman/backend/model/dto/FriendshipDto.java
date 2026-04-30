package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipDto {
    private Long id;
    private UserSummaryDto requester;
    private UserSummaryDto addressee;
    private String status;
    private ZonedDateTime createdAt;
    private ZonedDateTime respondedAt;
}

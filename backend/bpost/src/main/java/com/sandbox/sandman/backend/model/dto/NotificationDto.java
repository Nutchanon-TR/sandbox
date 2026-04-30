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
public class NotificationDto {
    private Long id;
    private String type;
    private UserSummaryDto actor;
    private Long targetId;
    private String targetKind;
    private String message;
    private ZonedDateTime createdAt;
    private ZonedDateTime readAt;
}

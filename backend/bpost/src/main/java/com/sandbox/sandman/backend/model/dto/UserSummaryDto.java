package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private Long id;
    private String supabaseUid;
    private String displayName;
    private String avatarUrl;
    private ZonedDateTime lastSeenAt;
}

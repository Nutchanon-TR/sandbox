package com.sandbox.sandman.backend.model.dto.ChatDto;

import lombok.Data;

@Data
public class UserResolveRequestDto {
    private String supabaseUid;
    private String email;
    private String username;
}

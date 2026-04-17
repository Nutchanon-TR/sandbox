package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileDto {
    private String supabaseUid;
    private String username;
    private String email;
    private String role;
    private String avatarUrl;
}

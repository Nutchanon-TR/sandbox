package com.sandbox.sandman.backend.model.dto;

import lombok.Data;

@Data
public class ProfileCreateRequestDto {
    private String supabaseUid;
    private String username;
    private String email;
}

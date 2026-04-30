package com.sandbox.sandman.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentCreateRequest {
    @NotBlank
    private String content;
}

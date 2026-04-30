package com.sandbox.sandman.backend.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateRequest {
    private String content;
    private List<String> imageUrls;
    private String visibility;
}

package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentDto {
    private Long id;
    private String type;
    private String url;
    private String mimeType;
    private String prompt;
    private String provider;
    private String metadata;
    private ZonedDateTime createdAt;
}

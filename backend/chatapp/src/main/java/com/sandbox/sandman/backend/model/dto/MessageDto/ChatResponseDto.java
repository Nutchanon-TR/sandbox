package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponseDto {
    private String reply;
    private List<ChatAttachmentDto> attachments = new ArrayList<>();

    public ChatResponseDto(String reply) {
        this.reply = reply;
        this.attachments = new ArrayList<>();
    }
}

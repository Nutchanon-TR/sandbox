package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChatHistoryResponse {
    private List<ChatDto> messages;
    private boolean hasMore;
}

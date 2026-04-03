package com.sandbox.sandman.backend.model.dto.ChatDto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MessageHistoryResponse {
    private List<MessageDto> messages;
    private boolean hasMore;
}

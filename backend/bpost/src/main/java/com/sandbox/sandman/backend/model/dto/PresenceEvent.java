package com.sandbox.sandman.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEvent {
    private Long userId;
    private boolean online;
}

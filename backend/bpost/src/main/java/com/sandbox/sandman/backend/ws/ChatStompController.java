package com.sandbox.sandman.backend.ws;

import com.sandbox.sandman.backend.model.dto.MessageDto;
import com.sandbox.sandman.backend.model.dto.MessageSendRequest;
import com.sandbox.sandman.backend.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final MessageService messageService;

    @MessageMapping("/message.send")
    public void onSend(@Payload MessageSendRequest req, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous STOMP send dropped");
            return;
        }
        Long senderId = parseId(principal.getName());
        if (senderId == null) return;
        try {
            MessageDto dto = messageService.send(senderId, req);
            log.debug("STOMP send ok: messageId={}", dto.getId());
        } catch (Exception e) {
            log.warn("STOMP send failed: {}", e.getMessage());
        }
    }

    @MessageMapping("/message.read")
    public void onRead(@Payload Map<String, Long> payload, Principal principal) {
        if (principal == null) return;
        Long userId = parseId(principal.getName());
        Long conversationId = payload.get("conversationId");
        if (userId == null || conversationId == null) return;
        try {
            messageService.markRead(userId, conversationId);
        } catch (Exception e) {
            log.warn("STOMP read failed: {}", e.getMessage());
        }
    }

    private Long parseId(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }
}

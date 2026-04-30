package com.sandbox.sandman.backend.ws;

import com.sandbox.sandman.backend.services.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        Principal user = event.getUser();
        Long id = parseId(user);
        if (id != null) presenceService.markOnline(id);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        Long id = parseId(user);
        if (id != null) presenceService.markOffline(id);
    }

    private Long parseId(Principal p) {
        if (p == null) return null;
        try { return Long.parseLong(p.getName()); } catch (Exception e) { return null; }
    }
}

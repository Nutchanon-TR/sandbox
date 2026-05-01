package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.PresenceEvent;
import com.sandbox.sandman.backend.commonauth.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String TOPIC_PRESENCE = "/topic/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthUserRepository userRepository;

    private final Set<Long> online = ConcurrentHashMap.newKeySet();

    public void markOnline(Long userId) {
        if (userId == null) return;
        boolean changed = online.add(userId);
        if (changed) {
            messagingTemplate.convertAndSend(TOPIC_PRESENCE, new PresenceEvent(userId, true));
        }
    }

    @Transactional
    public void markOffline(Long userId) {
        if (userId == null) return;
        boolean changed = online.remove(userId);
        try {
            userRepository.touchLastSeenAt(userId, ZonedDateTime.now());
        } catch (Exception ignored) {}
        if (changed) {
            messagingTemplate.convertAndSend(TOPIC_PRESENCE, new PresenceEvent(userId, false));
        }
    }

    public Set<Long> onlineUsers() {
        return Set.copyOf(online);
    }

    public boolean isOnline(Long userId) {
        return userId != null && online.contains(userId);
    }
}

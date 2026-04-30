package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.NotificationDto;
import com.sandbox.sandman.backend.model.entity.Notification;
import com.sandbox.sandman.backend.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String QUEUE_NOTIFICATIONS = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NotificationDto push(Long recipientId, String type, Long actorId,
                                Long targetId, String targetKind, String message) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return null; // don't notify self-actions
        }
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setActorId(actorId);
        n.setTargetId(targetId);
        n.setTargetKind(targetKind);
        n.setMessage(message);
        Notification saved = notificationRepository.save(n);
        NotificationDto dto = toDto(saved);
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(recipientId), QUEUE_NOTIFICATIONS, dto);
        } catch (Exception e) {
            log.warn("STOMP push failed for user {}: {}", recipientId, e.getMessage());
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long recipientId, boolean unreadOnly, int limit) {
        var pg = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        var rows = unreadOnly
                ? notificationRepository.findUnread(recipientId, pg)
                : notificationRepository.findByRecipientIdOrderByIdDesc(recipientId, pg);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public void markRead(Long recipientId, Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.getRecipientId().equals(recipientId)) {
                throw new IllegalStateException("Cannot mark someone else's notification");
            }
            if (n.getReadAt() == null) {
                n.setReadAt(ZonedDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .actor(userMapper.loadOne(n.getActorId()))
                .targetId(n.getTargetId())
                .targetKind(n.getTargetKind())
                .message(n.getMessage())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}

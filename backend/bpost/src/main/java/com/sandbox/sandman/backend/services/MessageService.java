package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ConversationDto;
import com.sandbox.sandman.backend.model.dto.MessageDto;
import com.sandbox.sandman.backend.model.dto.MessageSendRequest;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.entity.Conversation;
import com.sandbox.sandman.backend.model.entity.Message;
import com.sandbox.sandman.backend.model.entity.Notification;
import com.sandbox.sandman.backend.repositories.ConversationRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository;
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
public class MessageService {

    private static final String QUEUE_MESSAGES = "/queue/messages";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FriendService friendService;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Conversation getOrCreateConversation(Long me, Long other) {
        if (me.equals(other)) {
            throw new IllegalArgumentException("Cannot start a conversation with yourself");
        }
        if (!friendService.areFriends(me, other)) {
            throw new IllegalStateException("Must be friends to message");
        }
        Long a = Math.min(me, other);
        Long b = Math.max(me, other);
        return conversationRepository.findByUserAIdAndUserBId(a, b).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setUserAId(a);
            c.setUserBId(b);
            return conversationRepository.save(c);
        });
    }

    @Transactional
    public MessageDto send(Long senderId, MessageSendRequest req) {
        Conversation c;
        if (req.getConversationId() != null) {
            c = conversationRepository.findById(req.getConversationId())
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
            if (!senderId.equals(c.getUserAId()) && !senderId.equals(c.getUserBId())) {
                throw new IllegalStateException("Not a participant of this conversation");
            }
        } else {
            if (req.getRecipientId() == null) {
                throw new IllegalArgumentException("recipientId is required when conversationId is missing");
            }
            c = getOrCreateConversation(senderId, req.getRecipientId());
        }
        if ((req.getContent() == null || req.getContent().isBlank())
                && (req.getImageUrl() == null || req.getImageUrl().isBlank())) {
            throw new IllegalArgumentException("Message must have content or image");
        }

        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSenderId(senderId);
        m.setContent(req.getContent());
        m.setImageUrl(req.getImageUrl());
        Message saved = messageRepository.save(m);

        c.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(c);

        Long recipientId = senderId.equals(c.getUserAId()) ? c.getUserBId() : c.getUserAId();
        MessageDto dto = toDto(saved);

        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(recipientId), QUEUE_MESSAGES, dto);
            // Echo to sender so multi-tab stays in sync
            messagingTemplate.convertAndSendToUser(String.valueOf(senderId), QUEUE_MESSAGES, dto);
        } catch (Exception e) {
            log.warn("STOMP message push failed: {}", e.getMessage());
        }

        notificationService.push(recipientId, Notification.TYPE_MESSAGE,
                senderId, c.getId(), "conversation",
                req.getImageUrl() != null && !req.getImageUrl().isBlank() ? "sent you an image" : "sent you a message");

        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageDto> history(Long userId, Long conversationId, Long beforeId, int limit) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (!userId.equals(c.getUserAId()) && !userId.equals(c.getUserBId())) {
            throw new IllegalStateException("Not a participant of this conversation");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        var rows = messageRepository.findByConversation(conversationId, beforeId, PageRequest.of(0, safeLimit + 1));
        boolean hasMore = rows.size() > safeLimit;
        List<Message> page = hasMore ? rows.subList(0, safeLimit) : rows;
        // history returned newest-first; frontend reverses for chronological display
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResponse<>(page.stream().map(this::toDto).toList(), hasMore, nextCursor);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listForUser(Long userId) {
        return conversationRepository.findByUser(userId).stream().map(c -> {
            Long other = userId.equals(c.getUserAId()) ? c.getUserBId() : c.getUserAId();
            long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), userId);
            var lastRows = messageRepository.findByConversation(c.getId(), null, PageRequest.of(0, 1));
            MessageDto last = lastRows.isEmpty() ? null : toDto(lastRows.get(0));
            return ConversationDto.builder()
                    .id(c.getId())
                    .otherUser(userMapper.loadOne(other))
                    .lastMessageAt(c.getLastMessageAt())
                    .unreadCount(unread)
                    .lastMessage(last)
                    .build();
        }).toList();
    }

    @Transactional
    public int markRead(Long userId, Long conversationId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (!userId.equals(c.getUserAId()) && !userId.equals(c.getUserBId())) {
            throw new IllegalStateException("Not a participant of this conversation");
        }
        return messageRepository.markAllRead(conversationId, userId, ZonedDateTime.now());
    }

    private MessageDto toDto(Message m) {
        return MessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .content(m.getContent())
                .imageUrl(m.getImageUrl())
                .createdAt(m.getCreatedAt())
                .readAt(m.getReadAt())
                .build();
    }
}

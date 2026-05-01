package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.FriendshipDto;
import com.sandbox.sandman.backend.model.dto.UserSummaryDto;
import com.sandbox.sandman.backend.commonauth.AuthUser;
import com.sandbox.sandman.backend.model.entity.Friendship;
import com.sandbox.sandman.backend.model.entity.Notification;
import com.sandbox.sandman.backend.commonauth.AuthUserRepository;
import com.sandbox.sandman.backend.repositories.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final AuthUserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Transactional
    public FriendshipDto sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot friend yourself");
        }
        userRepository.findById(addresseeId)
                .orElseThrow(() -> new IllegalArgumentException("Addressee not found"));
        var existing = friendshipRepository.findByPair(requesterId, addresseeId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        Friendship f = new Friendship();
        f.setRequesterId(requesterId);
        f.setAddresseeId(addresseeId);
        f.setStatus(Friendship.STATUS_PENDING);
        Friendship saved = friendshipRepository.save(f);
        notificationService.push(addresseeId, Notification.TYPE_FRIEND_REQUEST,
                requesterId, saved.getId(), "friendship", "sent you a friend request");
        return toDto(saved);
    }

    @Transactional
    public FriendshipDto respond(Long userId, Long friendshipId, boolean accept) {
        Friendship f = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friendship not found"));
        if (!f.getAddresseeId().equals(userId)) {
            throw new IllegalStateException("Only addressee can respond to a friend request");
        }
        if (!Friendship.STATUS_PENDING.equals(f.getStatus())) {
            throw new IllegalStateException("Friendship is not pending");
        }
        f.setStatus(accept ? Friendship.STATUS_ACCEPTED : Friendship.STATUS_DECLINED);
        f.setRespondedAt(ZonedDateTime.now());
        Friendship saved = friendshipRepository.save(f);
        if (accept) {
            notificationService.push(saved.getRequesterId(), Notification.TYPE_FRIEND_ACCEPT,
                    userId, saved.getId(), "friendship", "accepted your friend request");
        }
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listFriends(Long userId) {
        var ids = friendshipRepository.findFriendIds(userId);
        if (ids.isEmpty()) return List.of();
        Map<Long, UserSummaryDto> map = userMapper.loadAll(ids);
        return ids.stream().map(map::get).filter(u -> u != null).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> friendIdsOf(Long userId) {
        return friendshipRepository.findFriendIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long a, Long b) {
        return friendshipRepository.findByPair(a, b)
                .map(f -> Friendship.STATUS_ACCEPTED.equals(f.getStatus()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<FriendshipDto> incomingPending(Long userId) {
        return friendshipRepository.findByAddresseeIdAndStatus(userId, Friendship.STATUS_PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipDto> outgoingPending(Long userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, Friendship.STATUS_PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> searchUsers(String q, Long excludeUserId) {
        if (q == null || q.isBlank()) return List.of();
        return userRepository.searchByDisplayName(q.trim()).stream()
                .filter(u -> !u.getId().equals(excludeUserId))
                .limit(20)
                .map(userMapper::toDto)
                .toList();
    }

    private FriendshipDto toDto(Friendship f) {
        AuthUser requester = userRepository.findById(f.getRequesterId()).orElse(null);
        AuthUser addressee = userRepository.findById(f.getAddresseeId()).orElse(null);
        return FriendshipDto.builder()
                .id(f.getId())
                .requester(userMapper.toDto(requester))
                .addressee(userMapper.toDto(addressee))
                .status(f.getStatus())
                .createdAt(f.getCreatedAt())
                .respondedAt(f.getRespondedAt())
                .build();
    }
}

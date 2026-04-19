package com.sandbox.sandman.backend.services.SyncHubService.CardService;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Friend;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.FriendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;

    @Transactional
    public void addFriend(Long userId, Long aiId) {
        if (friendRepository.existsByUserIdAndAiId(userId, aiId)) return;
        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setAiId(aiId);
        friendRepository.save(friend);
    }

    @Transactional
    public void removeFriend(Long userId, Long aiId) {
        friendRepository.deleteByUserIdAndAiId(userId, aiId);
    }

    public List<Friend> listFriends(Long userId) {
        return friendRepository.findByUserId(userId);
    }
}

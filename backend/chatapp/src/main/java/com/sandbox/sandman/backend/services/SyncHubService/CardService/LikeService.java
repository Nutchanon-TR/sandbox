package com.sandbox.sandman.backend.services.SyncHubService.CardService;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Like;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public void like(Long userId, Long aiId) {
        if (likeRepository.existsByUserIdAndAiId(userId, aiId)) return;
        Like like = new Like();
        like.setUserId(userId);
        like.setAiId(aiId);
        likeRepository.save(like);
    }

    @Transactional
    public void unlike(Long userId, Long aiId) {
        likeRepository.deleteByUserIdAndAiId(userId, aiId);
    }
}

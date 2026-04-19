package com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndAiId(Long userId, Long aiId);

    long countByAiId(Long aiId);

    long deleteByUserIdAndAiId(Long userId, Long aiId);
}

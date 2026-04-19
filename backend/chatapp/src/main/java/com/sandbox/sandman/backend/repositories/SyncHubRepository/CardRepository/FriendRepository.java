package com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {

    boolean existsByUserIdAndAiId(Long userId, Long aiId);

    long countByAiId(Long aiId);

    long deleteByUserIdAndAiId(Long userId, Long aiId);

    List<Friend> findByUserId(Long userId);
}

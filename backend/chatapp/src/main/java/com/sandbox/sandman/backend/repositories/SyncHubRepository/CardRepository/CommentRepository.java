package com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByAiIdOrderByCreatedAtDesc(Long aiId);

    long countByAiId(Long aiId);
}

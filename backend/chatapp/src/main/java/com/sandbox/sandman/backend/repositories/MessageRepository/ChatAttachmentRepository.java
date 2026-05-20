package com.sandbox.sandman.backend.repositories.MessageRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    List<ChatAttachment> findByChatIdIn(Collection<Long> chatIds);
}

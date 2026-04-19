package com.sandbox.sandman.backend.repositories.MessageRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Chat;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    List<Chat> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    @Query("SELECT m FROM Chat m WHERE m.room.id = :roomId ORDER BY m.createdAt DESC")
    List<Chat> findTopNByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT m FROM Chat m WHERE m.room.id = :roomId ORDER BY m.id DESC")
    List<Chat> findLatestByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT m FROM Chat m WHERE m.room.id = :roomId AND m.id < :beforeId ORDER BY m.id DESC")
    List<Chat> findByRoomIdBeforeId(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId, Pageable pageable);
}

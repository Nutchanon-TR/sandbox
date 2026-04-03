package com.sandbox.sandman.backend.repositories.ChatRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.ChatEntity.Message;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId ORDER BY m.createdAt DESC")
    List<Message> findTopNByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId ORDER BY m.id DESC")
    List<Message> findLatestByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.room.id = :roomId AND m.id < :beforeId ORDER BY m.id DESC")
    List<Message> findByRoomIdBeforeId(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId, Pageable pageable);
}

package com.sandbox.sandman.backend.repositories.ChatRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query(value = """
        SELECT r.* FROM chat.rooms r
        JOIN chat.room_members rm1 ON r.id = rm1.room_id
        JOIN chat.room_members rm2 ON r.id = rm2.room_id
        WHERE rm1.user_id = :userId AND rm2.user_id = :aiUserId
        AND r.is_group = false
        LIMIT 1
        """, nativeQuery = true)
    Optional<Room> findPrivateRoomBetween(@Param("userId") Long userId, @Param("aiUserId") Long aiUserId);

    @Query(value = """
        SELECT r.* FROM chat.rooms r
        JOIN chat.room_members rm ON r.id = rm.room_id
        WHERE rm.user_id = :userId
        ORDER BY r.created_at DESC
        """, nativeQuery = true)
    List<Room> findAllByUserId(@Param("userId") Long userId);
}

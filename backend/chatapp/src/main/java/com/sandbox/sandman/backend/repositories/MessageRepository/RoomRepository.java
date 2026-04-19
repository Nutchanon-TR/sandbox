package com.sandbox.sandman.backend.repositories.MessageRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Room;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query(value = """
        SELECT r.* FROM chat_app.rooms r
        WHERE r.user_id = :userId
        ORDER BY r.created_at DESC
        """, nativeQuery = true)
    List<Room> findAllByUserId(@Param("userId") Long userId);
}

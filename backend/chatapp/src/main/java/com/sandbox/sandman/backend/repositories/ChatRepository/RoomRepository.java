package com.sandbox.sandman.backend.repositories.ChatRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
}

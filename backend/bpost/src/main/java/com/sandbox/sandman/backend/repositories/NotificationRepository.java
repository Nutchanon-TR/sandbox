package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);

    @Query("""
        SELECT n FROM Notification n
        WHERE n.recipientId = :recipientId AND n.readAt IS NULL
        ORDER BY n.id DESC
        """)
    List<Notification> findUnread(@Param("recipientId") Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}

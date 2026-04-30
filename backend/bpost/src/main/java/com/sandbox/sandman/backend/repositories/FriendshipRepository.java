package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.requesterId = :a AND f.addresseeId = :b)
           OR (f.requesterId = :b AND f.addresseeId = :a)
        """)
    Optional<Friendship> findByPair(@Param("a") Long a, @Param("b") Long b);

    @Query("""
        SELECT CASE
                 WHEN f.requesterId = :userId THEN f.addresseeId
                 ELSE f.requesterId
               END
        FROM Friendship f
        WHERE f.status = 'ACCEPTED'
          AND (f.requesterId = :userId OR f.addresseeId = :userId)
        """)
    List<Long> findFriendIds(@Param("userId") Long userId);

    List<Friendship> findByAddresseeIdAndStatus(Long addresseeId, String status);

    List<Friendship> findByRequesterIdAndStatus(Long requesterId, String status);
}

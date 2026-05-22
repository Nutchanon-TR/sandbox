package com.sandbox.sandman.backend.repositories.PersonaFeedRepository;

import com.sandbox.sandman.backend.model.entity.PersonaFeedEntity.PersonaFeedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaFeedPostRepository extends JpaRepository<PersonaFeedPost, Long> {
}

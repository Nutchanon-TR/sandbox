package com.sandbox.sandman.backend.repositories.MessageRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.TrainingExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingExampleRepository extends JpaRepository<TrainingExample, Long> {
}

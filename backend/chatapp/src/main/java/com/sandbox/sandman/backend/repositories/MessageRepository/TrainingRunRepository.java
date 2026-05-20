package com.sandbox.sandman.backend.repositories.MessageRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.TrainingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingRunRepository extends JpaRepository<TrainingRun, Long> {
}

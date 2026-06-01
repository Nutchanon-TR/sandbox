package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.SearchRunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchRunEventRepository extends JpaRepository<SearchRunEvent, Long> {
    List<SearchRunEvent> findBySearchRunIdOrderByIdAsc(Long searchRunId);
}

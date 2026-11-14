package com.ektrepha.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmTask;

public interface CmTaskRepository extends JpaRepository<CmTask, Long> {

	Optional<CmTask> findByPublicId(String publicId);

	boolean existsBySourceEventId(UUID sourceEventId);

}

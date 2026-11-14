package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmAssignment;

public interface CmAssignmentRepository extends JpaRepository<CmAssignment, Long> {

	Optional<CmAssignment> findByParentIdAndUnassignedAtIsNull(Long parentId);

	Optional<CmAssignment> findByNannyIdAndUnassignedAtIsNull(Long nannyId);

}

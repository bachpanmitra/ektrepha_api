package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmAssignment;

public interface CmAssignmentRepository extends JpaRepository<CmAssignment, Long> {

	Optional<CmAssignment> findByParentIdAndUnassignedAtIsNull(Long parentId);

	Optional<CmAssignment> findByNannyIdAndUnassignedAtIsNull(Long nannyId);

	long countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(Long careManagerId);

	long countByCareManagerIdAndUnassignedAtIsNullAndNannyIsNotNull(Long careManagerId);

	/** Care Manager ParentDetail.cm_history — every assignment this parent has ever had, newest first. */
	List<CmAssignment> findByParentIdOrderByAssignedAtDesc(Long parentId);

}

package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;

public interface ParentChildRepository extends JpaRepository<ParentChild, ParentChildId> {

	boolean existsByIdParentIdAndIdChildId(Long parentId, Long childId);

	Optional<ParentChild> findByIdParentIdAndIdChildId(Long parentId, Long childId);

	// C1 — every child linked to this parent.
	List<ParentChild> findByIdParentId(Long parentId);

	// C5 — every guardian (this parent included) linked to a given child.
	List<ParentChild> findByIdChildId(Long childId);

	long countByIdChildId(Long childId);

}

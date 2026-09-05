package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;

public interface ParentChildRepository extends JpaRepository<ParentChild, ParentChildId> {

	boolean existsByIdParentIdAndIdChildId(Long parentId, Long childId);

}

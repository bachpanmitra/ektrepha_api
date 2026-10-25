package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentElder;
import com.ektrepha.model.ParentElderId;

public interface ParentElderRepository extends JpaRepository<ParentElder, ParentElderId> {

	Optional<ParentElder> findByIdParentIdAndIdElderId(Long parentId, Long elderId);

	// Elder Care's "list" — every elder linked to this parent, mirroring ParentChildRepository#findByIdParentId.
	List<ParentElder> findByIdParentId(Long parentId);

}

package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentAddress;

public interface ParentAddressRepository extends JpaRepository<ParentAddress, Long> {

	Optional<ParentAddress> findByParentIdAndPrimaryTrue(Long parentId);

	// Primary first, then oldest-first — matches P3's expected ordering (primary pinned to the top).
	List<ParentAddress> findByParentIdOrderByPrimaryDescIdAsc(Long parentId);

	Optional<ParentAddress> findByIdAndParentId(Long id, Long parentId);

}

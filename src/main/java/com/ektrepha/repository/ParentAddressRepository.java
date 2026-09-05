package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentAddress;

public interface ParentAddressRepository extends JpaRepository<ParentAddress, Long> {

	Optional<ParentAddress> findByParentIdAndPrimaryTrue(Long parentId);

}

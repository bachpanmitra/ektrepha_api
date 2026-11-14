package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmChangeRequest;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmTaskStatus;

public interface CmChangeRequestRepository extends JpaRepository<CmChangeRequest, Long> {

	Optional<CmChangeRequest> findByParentIdAndKindAndStatus(Long parentId, CmChangeRequestKind kind, CmTaskStatus status);

}

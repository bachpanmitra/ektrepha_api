package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyReference;
import com.ektrepha.model.VerificationRecordStatus;

public interface NannyReferenceRepository extends JpaRepository<NannyReference, Long> {

	List<NannyReference> findByNannyId(Long nannyId);

	long countByNannyIdAndStatus(Long nannyId, VerificationRecordStatus status);

}

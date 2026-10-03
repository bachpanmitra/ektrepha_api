package com.ektrepha.verification.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.NannyReferenceNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyReference;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.NannyReferenceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.verification.service.NannyReferenceService;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;

/** PRD: "Minimum 2 references, with verification status recorded by admin." Each reference is verified independently - the rollup (VerificationRollupCalculator) requires 2 VERIFIED, not just 2 submitted. */
@Service
@RequiredArgsConstructor
public class NannyReferenceServiceImpl implements NannyReferenceService {

	private final NannyReferenceRepository nannyReferenceRepository;
	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final NannyVerificationService nannyVerificationService;

	@Override
	@Transactional
	public NannyReference submit(Long userId, String name, String phone, String relationship) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));
		NannyReference reference = nannyReferenceRepository.save(NannyReference.builder()
				.nanny(nanny).name(name).phone(phone).relationship(relationship)
				.status(VerificationRecordStatus.PENDING)
				.build());
		nannyVerificationService.recompute(nanny.getId());
		return reference;
	}

	@Override
	@Transactional(readOnly = true)
	public List<NannyReference> list(Long nannyId) {
		return nannyReferenceRepository.findByNannyId(nannyId);
	}

	@Override
	@Transactional
	public NannyReference verify(Long referenceId, Long reviewedByUserId) {
		return updateStatus(referenceId, VerificationRecordStatus.VERIFIED, reviewedByUserId, null);
	}

	@Override
	@Transactional
	public NannyReference reject(Long referenceId, Long reviewedByUserId, String reason) {
		return updateStatus(referenceId, VerificationRecordStatus.REJECTED, reviewedByUserId, reason);
	}

	private NannyReference updateStatus(Long referenceId, VerificationRecordStatus status, Long reviewedByUserId, String reason) {
		NannyReference reference = nannyReferenceRepository.findById(referenceId)
				.orElseThrow(() -> new NannyReferenceNotFoundException("No reference with id " + referenceId));
		reference.setStatus(status);
		reference.setRejectionReason(reason);
		reference.setVerifiedAt(Instant.now());
		if (reviewedByUserId != null) {
			userRepository.findById(reviewedByUserId).ifPresent(reference::setVerifiedBy);
		}
		reference = nannyReferenceRepository.save(reference);
		nannyVerificationService.recompute(reference.getNanny().getId());
		return reference;
	}

}

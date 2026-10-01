package com.ektrepha.verification.impl;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.exception.InvalidVerificationDocumentException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.storage.S3PhotoUrlService;
import com.ektrepha.verification.VerificationRollupCalculator;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyVerificationServiceImpl implements NannyVerificationService {

	private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final S3PhotoUrlService s3PhotoUrlService;

	@Override
	@Transactional
	public NannyVerification submitDocument(Long userId, VerificationDocType type, MultipartFile file) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));
		return storeDocument(nanny, type, file);
	}

	@Override
	@Transactional
	public NannyVerification submitDocumentForNanny(Long nannyId, VerificationDocType type, MultipartFile file) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));
		return storeDocument(nanny, type, file);
	}

	private NannyVerification storeDocument(Nanny nanny, VerificationDocType type, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidVerificationDocumentException("No document was uploaded");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_DOCUMENT_CONTENT_TYPES.contains(contentType)) {
			throw new InvalidVerificationDocumentException("Document must be a JPEG, PNG, WEBP image or a PDF");
		}

		// A fresh key per upload (never reused), same reasoning as ChildServiceImpl#uploadPhoto -
		// a resubmission after rejection must not invalidate a presigned URL already handed out
		// for the old key.
		String key = "nanny-verification/%d/%s%s".formatted(nanny.getId(), UUID.randomUUID(), extensionFor(contentType));
		try {
			s3PhotoUrlService.upload(key, file.getBytes(), contentType);
		} catch (IOException e) {
			throw new InvalidVerificationDocumentException("Could not read the uploaded document");
		}

		NannyVerification record = NannyVerification.builder()
				.nanny(nanny)
				.type(type)
				.s3Key(key)
				.status(VerificationRecordStatus.PENDING)
				.build();
		record = nannyVerificationRepository.save(record);

		recompute(nanny.getId());
		log.info("Nanny verification document submitted: nannyId={}, type={}, recordId={}", nanny.getId(), type, record.getId());
		return record;
	}

	private String extensionFor(String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			case "application/pdf" -> ".pdf";
			default -> "";
		};
	}

	// Persists the status/reviewer/rejection-reason change on one verification record, then
	// recomputes the owning nanny's rollup in the same transaction — the trigger point required by
	// FR 4.3, so a status change and its rollup effect are never observably out of sync.
	@Override
	@Transactional
	public NannyVerification updateRecordStatus(Long verificationRecordId, VerificationRecordStatus newStatus, Long reviewedByUserId, String rejectionReason) {
		NannyVerification record = nannyVerificationRepository.findById(verificationRecordId)
				.orElseThrow(() -> new IllegalArgumentException("No nanny_verification row with id " + verificationRecordId));

		record.setStatus(newStatus);
		record.setRejectionReason(rejectionReason);
		record.setReviewedAt(Instant.now());
		if (reviewedByUserId != null) {
			userRepository.findById(reviewedByUserId).ifPresent(record::setReviewedBy);
		}
		record = nannyVerificationRepository.save(record);

		recompute(record.getNanny().getId());
		return record;
	}

	// Loads this nanny's current verification rows, applies the rollup rule, and persists the
	// result via the entity's dedicated recompute setter (never a plain field assignment).
	@Override
	@Transactional
	public void recompute(Long nannyId) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new IllegalArgumentException("No nanny with id " + nannyId));
		List<NannyVerification> records = nannyVerificationRepository.findByNannyId(nannyId);
		nanny.applyRecomputedVerificationStatus(VerificationRollupCalculator.compute(latestStatusPerType(records)));
		nannyRepository.save(nanny);
	}

	// Compares every nanny's stored rollup against what the rule would produce right now, in one
	// query pass (no per-nanny query loop) — this is what the scheduled audit job calls.
	@Override
	@Transactional(readOnly = true)
	public List<DriftRecord> findDrift() {
		Map<Long, List<NannyVerification>> recordsByNannyId = new HashMap<>();
		for (NannyVerification record : nannyVerificationRepository.findAllOrderedForAudit()) {
			recordsByNannyId.computeIfAbsent(record.getNanny().getId(), id -> new ArrayList<>()).add(record);
		}

		List<DriftRecord> drift = new ArrayList<>();
		for (Nanny nanny : nannyRepository.findAll()) {
			List<NannyVerification> records = recordsByNannyId.getOrDefault(nanny.getId(), List.of());
			NannyVerificationStatus expected = VerificationRollupCalculator.compute(latestStatusPerType(records));
			if (nanny.getOverallVerificationStatus() != expected) {
				drift.add(new DriftRecord(nanny.getId(), nanny.getOverallVerificationStatus(), expected));
			}
		}
		return drift;
	}

	// Reduces a nanny's (possibly multiple, e.g. resubmitted-after-rejection) verification rows to
	// the single latest row per type, by createdAt — the schema has no uniqueness constraint on
	// (nanny_id, type), so an older rejected row must never shadow a newer verified one.
	private Map<VerificationDocType, VerificationRecordStatus> latestStatusPerType(List<NannyVerification> records) {
		Map<VerificationDocType, NannyVerification> latestByType = new EnumMap<>(VerificationDocType.class);
		for (NannyVerification record : records) {
			NannyVerification current = latestByType.get(record.getType());
			if (current == null || record.getCreatedAt().isAfter(current.getCreatedAt())) {
				latestByType.put(record.getType(), record);
			}
		}
		Map<VerificationDocType, VerificationRecordStatus> result = new EnumMap<>(VerificationDocType.class);
		latestByType.forEach((type, record) -> result.put(type, record.getStatus()));
		return result;
	}

}

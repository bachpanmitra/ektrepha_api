package com.ektrepha.verification.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.exception.InvalidVerificationDocumentException;
import com.ektrepha.exception.NannyNotEligibleForApprovalException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.BannedIdentity;
import com.ektrepha.model.InterviewOutcome;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyInterview;
import com.ektrepha.model.NannyStatusHistory;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.BannedIdentityRepository;
import com.ektrepha.repository.NannyCodeOfConductAcceptanceRepository;
import com.ektrepha.repository.NannyInterviewRepository;
import com.ektrepha.repository.NannyReferenceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyStatusHistoryRepository;
import com.ektrepha.repository.NannyTrainingAttemptRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.storage.S3PhotoUrlService;
import com.ektrepha.verification.BanEvasionCheckService;
import com.ektrepha.verification.CodeOfConductDocument;
import com.ektrepha.verification.IdentityHashUtil;
import com.ektrepha.verification.VerificationRollupCalculator;
import com.ektrepha.verification.VerificationRollupCalculator.RollupInputs;
import com.ektrepha.verification.kyc.IdentityVerificationRequest;
import com.ektrepha.verification.kyc.IdentityVerificationResult;
import com.ektrepha.verification.kyc.KycVerificationProvider;
import com.ektrepha.verification.kyc.LivenessVerificationRequest;
import com.ektrepha.verification.kyc.LivenessVerificationResult;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyVerificationServiceImpl implements NannyVerificationService {

	private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
	private static final Set<NannyVerificationStatus> MANUAL_TARGET_STATUSES = Set.of(
			NannyVerificationStatus.UNDER_REVIEW, NannyVerificationStatus.APPROVED, NannyVerificationStatus.REJECTED,
			NannyVerificationStatus.SUSPENDED, NannyVerificationStatus.BANNED);

	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final S3PhotoUrlService s3PhotoUrlService;
	private final KycVerificationProvider kycVerificationProvider;
	private final BanEvasionCheckService banEvasionCheckService;
	private final NannyReferenceRepository nannyReferenceRepository;
	private final NannyInterviewRepository nannyInterviewRepository;
	private final NannyTrainingAttemptRepository nannyTrainingAttemptRepository;
	private final NannyCodeOfConductAcceptanceRepository nannyCodeOfConductAcceptanceRepository;
	private final NannyStatusHistoryRepository nannyStatusHistoryRepository;
	private final BannedIdentityRepository bannedIdentityRepository;

	@Override
	@Transactional
	public NannyVerification submitDocument(Long userId, VerificationDocType type, MultipartFile file, LocalDate expiryDate, String deviceId) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));
		if (deviceId != null && !deviceId.isBlank()) {
			banEvasionCheckService.checkDeviceId(deviceId);
			nanny.setDeviceId(deviceId);
			nannyRepository.save(nanny);
		}
		return storeDocument(nanny, type, file, expiryDate);
	}

	@Override
	@Transactional
	public NannyVerification submitDocumentForNanny(Long nannyId, VerificationDocType type, MultipartFile file, LocalDate expiryDate) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));
		return storeDocument(nanny, type, file, expiryDate);
	}

	private NannyVerification storeDocument(Nanny nanny, VerificationDocType type, MultipartFile file, LocalDate expiryDate) {
		if (file == null || file.isEmpty()) {
			throw new InvalidVerificationDocumentException("No document was uploaded");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_DOCUMENT_CONTENT_TYPES.contains(contentType)) {
			throw new InvalidVerificationDocumentException("Document must be a JPEG, PNG, WEBP image or a PDF");
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException e) {
			throw new InvalidVerificationDocumentException("Could not read the uploaded document");
		}

		// A fresh key per upload (never reused), same reasoning as ChildServiceImpl#uploadPhoto -
		// a resubmission after rejection must not invalidate a presigned URL already handed out
		// for the old key.
		String key = "nanny-verification/%d/%s%s".formatted(nanny.getId(), UUID.randomUUID(), extensionFor(contentType));
		s3PhotoUrlService.upload(key, bytes, contentType);

		NannyVerification.NannyVerificationBuilder builder = NannyVerification.builder()
				.nanny(nanny)
				.type(type)
				.s3Key(key)
				.status(VerificationRecordStatus.PENDING);

		// ID_PROOF and LIVENESS_SELFIE are the two ban-evasion checkpoints: a vendor-extracted hash
		// is checked against every previously-banned identity BEFORE this row (and therefore any
		// path towards APPROVED) is persisted - see BanEvasionCheckService's javadoc for why phone
		// is also covered, separately, at account-creation time.
		if (type == VerificationDocType.ID_PROOF) {
			IdentityVerificationResult kycResult = kycVerificationProvider.verifyIdentity(new IdentityVerificationRequest(nanny.getId(), bytes, contentType));
			banEvasionCheckService.checkIdDocumentHash(kycResult.idDocumentHash());
			builder.idDocHash(kycResult.idDocumentHash()).vendorReferenceId(kycResult.vendorReferenceId());
		} else if (type == VerificationDocType.LIVENESS_SELFIE) {
			LivenessVerificationResult liveness = kycVerificationProvider.verifyLiveness(new LivenessVerificationRequest(nanny.getId(), bytes, contentType));
			banEvasionCheckService.checkFaceEmbeddingHash(liveness.faceEmbeddingHash());
			builder.faceEmbeddingHash(liveness.faceEmbeddingHash()).vendorReferenceId(liveness.vendorReferenceId());
		}
		if (type == VerificationDocType.BACKGROUND_CHECK) {
			builder.expiryDate(expiryDate);
		}

		NannyVerification record = nannyVerificationRepository.save(builder.build());

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
	// recomputes the owning nanny's rollup in the same transaction — a status change and its
	// rollup effect must never be observably out of sync.
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

	// Loads this nanny's full set of onboarding signals, applies the rollup rule, and persists the
	// result via the entity's dedicated recompute setter (never a plain field assignment). No-op
	// on SUSPENDED/BANNED nannies - see Nanny#applyRecomputedVerificationStatus.
	@Override
	@Transactional
	public void recompute(Long nannyId) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new IllegalArgumentException("No nanny with id " + nannyId));
		nanny.applyRecomputedVerificationStatus(VerificationRollupCalculator.compute(gatherRollupInputs(nanny)));
		nannyRepository.save(nanny);
	}

	@Override
	@Transactional
	public void changeStatus(Long nannyId, NannyVerificationStatus newStatus, String reason, Long changedByUserId) {
		if (!MANUAL_TARGET_STATUSES.contains(newStatus)) {
			throw new IllegalArgumentException(newStatus + " cannot be set manually - it is only ever produced by the automatic recompute path");
		}
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));

		if (newStatus == NannyVerificationStatus.APPROVED && VerificationRollupCalculator.compute(gatherRollupInputs(nanny)) != NannyVerificationStatus.APPROVED) {
			throw new NannyNotEligibleForApprovalException(
					"This nanny has not met every verification gate yet (documents, 2+ references, interview, training, code of conduct, age) - cannot approve.");
		}

		User changedBy = changedByUserId == null ? null : userRepository.findById(changedByUserId).orElse(null);
		NannyVerificationStatus previousStatus = nanny.getOverallVerificationStatus();

		nanny.applyManualStatusChange(newStatus, reason, changedBy);
		nannyRepository.save(nanny);

		nannyStatusHistoryRepository.save(NannyStatusHistory.builder()
				.nanny(nanny).previousStatus(previousStatus).newStatus(newStatus).reason(reason).changedBy(changedBy).build());

		if (newStatus == NannyVerificationStatus.BANNED) {
			seedBannedIdentity(nanny, reason);
		}
		log.info("Nanny status changed: nannyId={}, {} -> {}, changedBy={}, reason={}", nannyId, previousStatus, newStatus, changedByUserId, reason);
	}

	// Pulls this nanny's known identity signals (phone, latest ID_PROOF/LIVENESS_SELFIE hashes,
	// device id) into banned_identity, so a new signup attempt under a different account - but the
	// same phone/document/face/device - is caught by BanEvasionCheckService.
	private void seedBannedIdentity(Nanny nanny, String reason) {
		Map<VerificationDocType, NannyVerification> latestByType = latestRecordPerType(nannyVerificationRepository.findByNannyId(nanny.getId()));
		NannyVerification idProof = latestByType.get(VerificationDocType.ID_PROOF);
		NannyVerification liveness = latestByType.get(VerificationDocType.LIVENESS_SELFIE);
		String phone = nanny.getUser() != null ? nanny.getUser().getPhone() : null;

		bannedIdentityRepository.save(BannedIdentity.builder()
				.phoneHash(IdentityHashUtil.sha256Hex(phone))
				.idDocHash(idProof != null ? idProof.getIdDocHash() : null)
				.faceEmbeddingHash(liveness != null ? liveness.getFaceEmbeddingHash() : null)
				.deviceId(nanny.getDeviceId())
				.bannedNanny(nanny)
				.reason(reason)
				.build());
	}

	// One pass per nanny (not the single-query batch the pre-overhaul version used) - findDrift
	// now needs five signal sources per nanny (docs/references/interview/training/code-of-conduct)
	// instead of one, and this is a once-a-day scheduled job over a pre-launch-scale nanny pool, so
	// correctness-over-optimization is the right trade here; revisit if the audit job's runtime
	// ever becomes a problem.
	@Override
	@Transactional(readOnly = true)
	public List<DriftRecord> findDrift() {
		List<DriftRecord> drift = new ArrayList<>();
		for (Nanny nanny : nannyRepository.findAll()) {
			if (nanny.getOverallVerificationStatus() == NannyVerificationStatus.SUSPENDED
					|| nanny.getOverallVerificationStatus() == NannyVerificationStatus.BANNED) {
				continue;
			}
			NannyVerificationStatus expected = VerificationRollupCalculator.compute(gatherRollupInputs(nanny));
			if (nanny.getOverallVerificationStatus() != expected) {
				drift.add(new DriftRecord(nanny.getId(), nanny.getOverallVerificationStatus(), expected));
			}
		}
		return drift;
	}

	private RollupInputs gatherRollupInputs(Nanny nanny) {
		Map<VerificationDocType, NannyVerification> latestByType = latestRecordPerType(nannyVerificationRepository.findByNannyId(nanny.getId()));
		Map<VerificationDocType, VerificationRecordStatus> latestStatusByType = new EnumMap<>(VerificationDocType.class);
		latestByType.forEach((type, record) -> latestStatusByType.put(type, record.getStatus()));

		NannyVerification latestPcc = latestByType.get(VerificationDocType.BACKGROUND_CHECK);
		LocalDate verifiedPccExpiryDate = latestPcc != null && latestPcc.getStatus() == VerificationRecordStatus.VERIFIED ? latestPcc.getExpiryDate() : null;

		List<com.ektrepha.model.NannyReference> references = nannyReferenceRepository.findByNannyId(nanny.getId());
		int referenceCount = references.size();
		int verifiedReferenceCount = (int) references.stream().filter(r -> r.getStatus() == VerificationRecordStatus.VERIFIED).count();

		List<NannyInterview> interviews = nannyInterviewRepository.findByNannyIdOrderByCreatedAtDesc(nanny.getId());
		InterviewOutcome latestInterviewOutcome = interviews.isEmpty() ? null : interviews.get(0).getOutcome();

		boolean trainingPassed = nannyTrainingAttemptRepository.existsByNannyIdAndPassedTrue(nanny.getId());
		boolean codeOfConductAccepted = nannyCodeOfConductAcceptanceRepository.existsByNannyIdAndVersion(nanny.getId(), CodeOfConductDocument.CURRENT_VERSION);

		return new RollupInputs(nanny.isAtLeast18(), latestStatusByType, verifiedPccExpiryDate, referenceCount, verifiedReferenceCount,
				latestInterviewOutcome, trainingPassed, codeOfConductAccepted);
	}

	// Reduces a nanny's (possibly multiple, e.g. resubmitted-after-rejection) verification rows to
	// the single latest row per type, by createdAt — the schema has no uniqueness constraint on
	// (nanny_id, type), so an older rejected row must never shadow a newer verified one.
	private Map<VerificationDocType, NannyVerification> latestRecordPerType(List<NannyVerification> records) {
		Map<VerificationDocType, NannyVerification> latestByType = new EnumMap<>(VerificationDocType.class);
		for (NannyVerification record : records) {
			NannyVerification current = latestByType.get(record.getType());
			if (current == null || record.getCreatedAt().isAfter(current.getCreatedAt())) {
				latestByType.put(record.getType(), record);
			}
		}
		return latestByType;
	}

}

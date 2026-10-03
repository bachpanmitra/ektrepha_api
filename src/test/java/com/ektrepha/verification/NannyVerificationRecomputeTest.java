package com.ektrepha.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.BanEvasionDetectedException;
import com.ektrepha.exception.NannyNotEligibleForApprovalException;
import com.ektrepha.model.BannedIdentity;
import com.ektrepha.model.InterviewOutcome;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyCodeOfConductAcceptance;
import com.ektrepha.model.NannyInterview;
import com.ektrepha.model.NannyReference;
import com.ektrepha.model.NannyTrainingAttempt;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.BannedIdentityRepository;
import com.ektrepha.repository.NannyCodeOfConductAcceptanceRepository;
import com.ektrepha.repository.NannyInterviewRepository;
import com.ektrepha.repository.NannyReferenceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyTrainingAttemptRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.verification.service.NannyVerificationService;
import com.ektrepha.verification.service.NannyVerificationService.DriftRecord;

import jakarta.persistence.EntityManager;

/**
 * Regression coverage for the caregiver-verification state machine (migration 38): the rollup
 * rule (documents + references + interview + training + code of conduct + age), the manual
 * status-change path (approve/reject/suspend/ban), and ban-evasion detection. Every test method
 * rolls back automatically ({@code @Transactional}), so this leaves no residue in the shared dev
 * database.
 */
@SpringBootTest
@Transactional
class NannyVerificationRecomputeTest {

	@Autowired
	private NannyVerificationService nannyVerificationService;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyVerificationRepository nannyVerificationRepository;
	@Autowired
	private NannyReferenceRepository nannyReferenceRepository;
	@Autowired
	private NannyInterviewRepository nannyInterviewRepository;
	@Autowired
	private NannyTrainingAttemptRepository nannyTrainingAttemptRepository;
	@Autowired
	private NannyCodeOfConductAcceptanceRepository nannyCodeOfConductAcceptanceRepository;
	@Autowired
	private BannedIdentityRepository bannedIdentityRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NamedParameterJdbcTemplate jdbcTemplate;
	@Autowired
	private EntityManager entityManager;

	private Nanny createNanny(LocalDate dob) {
		User user = userRepository.save(User.builder()
				.name("Verification Test Nanny")
				.phone("+9199" + (System.nanoTime() % 100000000L))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Verify").lastName("Test").dob(dob)
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private Nanny createAdultNanny() {
		return createNanny(LocalDate.now().minusYears(25));
	}

	private NannyVerification createDoc(Nanny nanny, VerificationDocType type, VerificationRecordStatus status) {
		sleep();
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(type).status(status).s3Key("test/" + type + "/" + System.nanoTime()).build());
	}

	private void createVerifiedReference(Nanny nanny) {
		nannyReferenceRepository.save(NannyReference.builder()
				.nanny(nanny).name("Ref").phone("+919000000000").status(VerificationRecordStatus.VERIFIED).build());
	}

	private void createInterview(Nanny nanny, InterviewOutcome outcome) {
		nannyInterviewRepository.save(NannyInterview.builder()
				.nanny(nanny).scheduledAt(Instant.now()).outcome(outcome).build());
	}

	private void createPassedTraining(Nanny nanny) {
		nannyTrainingAttemptRepository.save(NannyTrainingAttempt.builder()
				.nanny(nanny).moduleVersion("v1").score(100).passed(true).startedAt(Instant.now()).completedAt(Instant.now()).build());
	}

	private void acceptCodeOfConduct(Nanny nanny) {
		nannyCodeOfConductAcceptanceRepository.save(NannyCodeOfConductAcceptance.builder()
				.nanny(nanny).version(CodeOfConductDocument.CURRENT_VERSION).acceptedAt(Instant.now()).build());
	}

	/** Brings every gate except the documents to a passing state - tests then only vary the four required doc types. */
	private Nanny createNannyWithNonDocGatesSatisfied() {
		Nanny nanny = createAdultNanny();
		createVerifiedReference(nanny);
		createVerifiedReference(nanny);
		createInterview(nanny, InterviewOutcome.PASSED);
		createPassedTraining(nanny);
		acceptCodeOfConduct(nanny);
		return nanny;
	}

	private void sleep() {
		try {
			// Ensures distinct createdAt ordering for "latest per type" tests without relying on clock granularity.
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	void allGatesMet_rollsUpToApproved() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.APPROVED);
	}

	@Test
	void someButNotAllRequiredDocsVerified_rollsUpToUnderReview() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.PENDING);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.UNDER_REVIEW);
	}

	@Test
	void fewerThanTwoVerifiedReferences_blocksApproval() {
		Nanny nanny = createAdultNanny();
		createVerifiedReference(nanny); // only 1, not 2
		createInterview(nanny, InterviewOutcome.PASSED);
		createPassedTraining(nanny);
		acceptCodeOfConduct(nanny);
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.UNDER_REVIEW);
	}

	@Test
	void expiredVerifiedPcc_blocksApproval() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		NannyVerification pcc = createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		pcc.setExpiryDate(LocalDate.now().minusDays(1));
		nannyVerificationRepository.save(pcc);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.UNDER_REVIEW);
	}

	// Exercised as a pure calculator unit test, not through a persisted Nanny: the DB's own
	// nanny_dob_18_plus_check CHECK constraint (migration 38) makes an actual under-18 nanny row
	// impossible to insert at all - this is the defense-in-depth Java-layer gate (Nanny#isAtLeast18,
	// used for a nanny with no dob on file yet) tested in isolation.
	@Test
	void under18_blocksApprovalEvenWithEveryOtherGateMet() {
		var latestStatusByType = new java.util.EnumMap<VerificationDocType, VerificationRecordStatus>(VerificationDocType.class);
		for (VerificationDocType type : List.of(VerificationDocType.ID_PROOF, VerificationDocType.BACKGROUND_CHECK,
				VerificationDocType.ADDRESS_PROOF, VerificationDocType.LIVENESS_SELFIE)) {
			latestStatusByType.put(type, VerificationRecordStatus.VERIFIED);
		}
		var inputs = new VerificationRollupCalculator.RollupInputs(
				false, latestStatusByType, null, 2, 2, InterviewOutcome.PASSED, true, true);

		assertThat(VerificationRollupCalculator.compute(inputs)).isEqualTo(NannyVerificationStatus.UNDER_REVIEW);
	}

	@Test
	void noSignalsAtAll_rollsUpToPending() {
		Nanny nanny = createNanny(null);
		nanny.applyRecomputedVerificationStatus(NannyVerificationStatus.APPROVED); // wrong on purpose, to prove recompute corrects it
		nannyRepository.save(nanny);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.PENDING);
	}

	@Test
	void anyRequiredDocRejected_winsOverEverythingElsePassing() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.REJECTED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.REJECTED);
	}

	@Test
	void interviewFailed_rollsUpToRejectedEvenWithEverythingElsePassing() {
		Nanny nanny = createAdultNanny();
		createVerifiedReference(nanny);
		createVerifiedReference(nanny);
		createInterview(nanny, InterviewOutcome.FAILED);
		createPassedTraining(nanny);
		acceptCodeOfConduct(nanny);
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.REJECTED);
	}

	@Test
	void resubmissionAfterRejection_latestRecordPerTypeWins() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.REJECTED);
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED); // resubmitted, newer
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.APPROVED);
	}

	@Test
	void findDrift_isEmptyWhenStoredStatusIsCorrect() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);
		nannyVerificationService.recompute(nanny.getId());

		List<DriftRecord> drift = nannyVerificationService.findDrift();

		assertThat(drift).noneMatch(d -> d.nannyId().equals(nanny.getId()));
	}

	@Test
	void findDrift_detectsStatusChangedOutsideTheServiceLayer() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);
		nannyVerificationService.recompute(nanny.getId());

		// Simulates drift: a direct SQL change bypassing NannyVerificationService entirely.
		jdbcTemplate.update("UPDATE nanny SET overall_verification_status = :status WHERE id = :id",
				new MapSqlParameterSource().addValue("status", (short) 1).addValue("id", nanny.getId()));
		entityManager.clear();

		List<DriftRecord> drift = nannyVerificationService.findDrift();

		Optional<DriftRecord> found = drift.stream().filter(d -> d.nannyId().equals(nanny.getId())).findFirst();
		assertThat(found).isPresent();
		assertThat(found.get().storedStatus()).isEqualTo(NannyVerificationStatus.PENDING);
		assertThat(found.get().expectedStatus()).isEqualTo(NannyVerificationStatus.APPROVED);
	}

	@Test
	void findDrift_neverFlagsASuspendedOrBannedNanny() {
		Nanny nanny = createAdultNanny(); // recompute would land this on UNDER_REVIEW, but the status is forced to SUSPENDED below
		nannyVerificationService.changeStatus(nanny.getId(), NannyVerificationStatus.SUSPENDED, "test suspension", null);

		List<DriftRecord> drift = nannyVerificationService.findDrift();

		assertThat(drift).noneMatch(d -> d.nannyId().equals(nanny.getId()));
	}

	@Test
	void changeStatus_toPending_isRejected() {
		Nanny nanny = createAdultNanny();

		assertThatThrownBy(() -> nannyVerificationService.changeStatus(nanny.getId(), NannyVerificationStatus.PENDING, "nope", null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void changeStatus_toApproved_withoutMeetingEveryGate_isRejected() {
		Nanny nanny = createAdultNanny(); // no documents/references/interview/training/code-of-conduct at all

		assertThatThrownBy(() -> nannyVerificationService.changeStatus(nanny.getId(), NannyVerificationStatus.APPROVED, "sign off", null))
				.isInstanceOf(NannyNotEligibleForApprovalException.class);
	}

	@Test
	void changeStatus_toSuspended_survivesAFollowingRecompute() {
		Nanny nanny = createNannyWithNonDocGatesSatisfied();
		createDoc(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.ADDRESS_PROOF, VerificationRecordStatus.VERIFIED);
		createDoc(nanny, VerificationDocType.LIVENESS_SELFIE, VerificationRecordStatus.VERIFIED);
		nannyVerificationService.recompute(nanny.getId()); // -> APPROVED

		nannyVerificationService.changeStatus(nanny.getId(), NannyVerificationStatus.SUSPENDED, "expired PCC", null);
		// A later doc re-review must not silently pop this back to APPROVED.
		nannyVerificationService.recompute(nanny.getId());

		Nanny reloaded = nannyRepository.findById(nanny.getId()).orElseThrow();
		assertThat(reloaded.getOverallVerificationStatus()).isEqualTo(NannyVerificationStatus.SUSPENDED);
		assertThat(reloaded.getStatusReason()).isEqualTo("expired PCC");
	}

	@Test
	void changeStatus_toBanned_seedsBannedIdentityWithThisNannysPhoneHash() {
		Nanny nanny = createAdultNanny();
		String phone = nanny.getUser().getPhone();

		nannyVerificationService.changeStatus(nanny.getId(), NannyVerificationStatus.BANNED, "safety violation", null);

		Optional<BannedIdentity> seeded = bannedIdentityRepository.findFirstByPhoneHash(IdentityHashUtil.sha256Hex(phone));
		assertThat(seeded).isPresent();
		assertThat(seeded.get().getReason()).isEqualTo("safety violation");
	}

	@Test
	void banEvasionCheckService_throwsOnAMatchingHash() {
		bannedIdentityRepository.save(BannedIdentity.builder().phoneHash(IdentityHashUtil.sha256Hex("+919999999999")).reason("banned").build());

		assertThatThrownBy(() -> new BanEvasionCheckService(bannedIdentityRepository).checkPhoneHash(IdentityHashUtil.sha256Hex("+919999999999")))
				.isInstanceOf(BanEvasionDetectedException.class);
	}

}

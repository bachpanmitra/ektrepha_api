package com.ektrepha.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.verification.service.NannyVerificationService;
import com.ektrepha.verification.service.NannyVerificationService.DriftRecord;

import jakarta.persistence.EntityManager;

/**
 * Regression coverage for the "Verification Status Filtering in Search" PRD's recompute rule and
 * drift audit — the PRD explicitly asks for this to be a real test, not just code review. Every
 * test method rolls back automatically ({@code @Transactional}), so this leaves no residue in the
 * shared dev database.
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
	private UserRepository userRepository;
	@Autowired
	private NamedParameterJdbcTemplate jdbcTemplate;
	@Autowired
	private EntityManager entityManager;

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Verification Test Nanny")
				.email("verify-test-" + System.nanoTime() + "@example.com")
				.active(true)
				.emailVerified(true)
				.phoneVerified(true)
				.userSource(UserSource.EMAIL)
				.userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user)
				.firstName("Verify")
				.lastName("Test")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private NannyVerification createRecord(Nanny nanny, VerificationDocType type, VerificationRecordStatus status) {
		try {
			// Ensures distinct createdAt ordering for "latest per type" tests without relying on clock granularity.
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny)
				.type(type)
				.status(status)
				.s3Key("test/" + type + "/" + System.nanoTime())
				.build());
	}

	@Test
	void allRequiredTypesVerified_rollsUpToVerified() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.VERIFIED);
	}

	@Test
	void someButNotAllRequiredTypesVerified_rollsUpToPartial() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.PENDING);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.PARTIAL);
	}

	@Test
	void noVerificationRecords_rollsUpToPending() {
		// Start from a wrong status to prove recompute corrects it, not just leaves PENDING alone.
		Nanny nanny = createNanny();
		nanny.applyRecomputedVerificationStatus(NannyVerificationStatus.VERIFIED);
		nannyRepository.save(nanny);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.PENDING);
	}

	@Test
	void anyRequiredTypeRejected_winsOverVerifiedOthers() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.REJECTED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.REJECTED);
	}

	@Test
	void nonRequiredTypeStatus_neverAffectsRollup() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.FIRST_AID, VerificationRecordStatus.REJECTED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.VERIFIED);
	}

	@Test
	void resubmissionAfterRejection_latestRecordPerTypeWins() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.REJECTED);
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED); // resubmitted, newer
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);

		nannyVerificationService.recompute(nanny.getId());

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.VERIFIED);
	}

	@Test
	void updateRecordStatus_triggersRecomputeInSameTransaction() {
		Nanny nanny = createNanny();
		NannyVerification idProof = createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.PENDING);
		NannyVerification backgroundCheck = createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.PENDING);
		NannyVerification education = createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.PENDING);

		nannyVerificationService.updateRecordStatus(idProof.getId(), VerificationRecordStatus.VERIFIED, null, null);
		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.PARTIAL);

		nannyVerificationService.updateRecordStatus(backgroundCheck.getId(), VerificationRecordStatus.VERIFIED, null, null);
		nannyVerificationService.updateRecordStatus(education.getId(), VerificationRecordStatus.VERIFIED, null, null);

		assertThat(nannyRepository.findById(nanny.getId()).orElseThrow().getOverallVerificationStatus())
				.isEqualTo(NannyVerificationStatus.VERIFIED);
	}

	@Test
	void findDrift_isEmptyWhenStoredStatusIsCorrect() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);
		nannyVerificationService.recompute(nanny.getId());

		List<DriftRecord> drift = nannyVerificationService.findDrift();

		assertThat(drift).noneMatch(d -> d.nannyId().equals(nanny.getId()));
	}

	@Test
	void findDrift_detectsStatusChangedOutsideTheServiceLayer() {
		Nanny nanny = createNanny();
		createRecord(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createRecord(nanny, VerificationDocType.EDUCATION, VerificationRecordStatus.VERIFIED);
		nannyVerificationService.recompute(nanny.getId());

		// Simulates drift: a direct SQL change bypassing NannyVerificationService entirely — exactly
		// the scenario the PRD's "not just code review" success metric is meant to catch.
		jdbcTemplate.update("UPDATE nanny SET overall_verification_status = :status WHERE id = :id",
				new MapSqlParameterSource().addValue("status", (short) 1).addValue("id", nanny.getId()));
		entityManager.clear();

		List<DriftRecord> drift = nannyVerificationService.findDrift();

		Optional<DriftRecord> found = drift.stream().filter(d -> d.nannyId().equals(nanny.getId())).findFirst();
		assertThat(found).isPresent();
		assertThat(found.get().storedStatus()).isEqualTo(NannyVerificationStatus.PENDING);
		assertThat(found.get().expectedStatus()).isEqualTo(NannyVerificationStatus.VERIFIED);
	}

}

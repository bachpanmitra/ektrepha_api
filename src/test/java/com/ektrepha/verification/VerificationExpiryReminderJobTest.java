package com.ektrepha.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.ektrepha.verification.impl.VerificationExpiryAuditJob;

/** API Map row 26 — the 30-days-before-expiry reminder sweep. */
@SpringBootTest
@Transactional
class VerificationExpiryReminderJobTest {

	@Autowired
	private VerificationExpiryAuditJob job;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyVerificationRepository nannyVerificationRepository;

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Expiry Reminder Nanny").phone("+9190055" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Expiry").lastName("Test")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private NannyVerification createPcc(Nanny nanny, LocalDate expiryDate) {
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(VerificationDocType.BACKGROUND_CHECK).s3Key("test/pcc.jpg")
				.status(VerificationRecordStatus.VERIFIED).expiryDate(expiryDate)
				.build());
	}

	@Test
	void pccExpiringWithin30Days_getsReminderMarkedSent() {
		Nanny nanny = createNanny();
		NannyVerification pcc = createPcc(nanny, LocalDate.now().plusDays(10));

		job.auditExpiryAndReverification();

		NannyVerification reloaded = nannyVerificationRepository.findById(pcc.getId()).orElseThrow();
		assertThat(reloaded.getExpiryReminderSentAt()).isNotNull();
	}

	@Test
	void pccExpiringBeyond30Days_isNotReminded() {
		Nanny nanny = createNanny();
		NannyVerification pcc = createPcc(nanny, LocalDate.now().plusDays(45));

		job.auditExpiryAndReverification();

		NannyVerification reloaded = nannyVerificationRepository.findById(pcc.getId()).orElseThrow();
		assertThat(reloaded.getExpiryReminderSentAt()).isNull();
	}

	@Test
	void alreadyReminded_isNotRemindedAgain() {
		Nanny nanny = createNanny();
		NannyVerification pcc = createPcc(nanny, LocalDate.now().plusDays(5));
		pcc.setExpiryReminderSentAt(java.time.Instant.now().minusSeconds(3600));
		nannyVerificationRepository.save(pcc);

		job.auditExpiryAndReverification();

		NannyVerification reloaded = nannyVerificationRepository.findById(pcc.getId()).orElseThrow();
		assertThat(reloaded.getExpiryReminderSentAt()).isBefore(java.time.Instant.now().minusSeconds(60));
	}

}

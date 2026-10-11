package com.ektrepha.nannyapp.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyEmployment;
import com.ektrepha.model.NannyTrainingSession;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.nannyapp.dto.request.BankAccountRequest;
import com.ektrepha.nannyapp.dto.request.EmergencyContactRequest;
import com.ektrepha.nannyapp.dto.request.UpdateMeRequest;
import com.ektrepha.nannyapp.dto.response.BankAccountResponse;
import com.ektrepha.nannyapp.dto.response.DocumentResponse;
import com.ektrepha.nannyapp.dto.response.EmergencyContactResponse;
import com.ektrepha.nannyapp.dto.response.NannyMeResponse;
import com.ektrepha.nannyapp.dto.response.OnboardingResponse;
import com.ektrepha.nannyapp.dto.response.OnboardingStepResponse;
import com.ektrepha.nannyapp.dto.response.StatutoryResponse;
import com.ektrepha.nannyapp.dto.response.TrainingResponse;
import com.ektrepha.nannyapp.service.NannyAppProfileService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyTrainingAttemptRepository;
import com.ektrepha.repository.NannyTrainingSessionRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.storage.S3PhotoUrlService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NannyAppProfileServiceImpl implements NannyAppProfileService {

	/** A check-in this long after the booking's start still counts as on time. */
	static final long ON_TIME_GRACE_MINUTES = 10;
	private static final int ON_TIME_LOOKBACK_DAYS = 90;

	private final NannyAppSupport support;
	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannyTrainingSessionRepository nannyTrainingSessionRepository;
	private final NannyTrainingAttemptRepository nannyTrainingAttemptRepository;
	private final ReviewRepository reviewRepository;
	private final BookingRepository bookingRepository;
	private final S3PhotoUrlService s3PhotoUrlService;

	@Value("${app.nanny-app.ops-manager-name:Ektrepha ops}")
	private String defaultManagerName;
	@Value("${app.nanny-app.ops-manager-phone:}")
	private String defaultManagerPhone;
	@Value("${app.nanny-app.hr-contact-name:Ektrepha HR}")
	private String hrContactName;
	@Value("${app.nanny-app.hr-contact-hours:Mon-Sat, 9 AM - 6 PM}")
	private String hrContactHours;
	@Value("${app.nanny-app.hr-contact-phone:}")
	private String hrContactPhone;

	@Override
	@Transactional(readOnly = true)
	public NannyMeResponse me(Long userId) {
		Nanny nanny = support.nanny(userId);
		return toMe(nanny, support.employment(nanny));
	}

	@Override
	@Transactional
	public NannyMeResponse updateMe(Long userId, UpdateMeRequest request) {
		Nanny nanny = support.nanny(userId);
		NannyEmployment employment = support.employment(nanny);
		if (request.language() != null) {
			employment.setAppLanguage(request.language());
			employment = support.save(employment);
		}
		return toMe(nanny, employment);
	}

	@Override
	@Transactional(readOnly = true)
	public OnboardingResponse onboarding(Long userId) {
		Nanny nanny = support.nanny(userId);
		NannyEmployment employment = support.employment(nanny);
		List<NannyVerification> docs = nannyVerificationRepository.findByNannyId(nanny.getId());
		List<NannyTrainingSession> sessions = nannyTrainingSessionRepository.findByNannyIdOrderByStartDateAsc(nanny.getId());

		List<OnboardingStepResponse> steps = new ArrayList<>();
		steps.add(employment.getOfferLetterSignedAt() != null
				? step("OFFER_LETTER", "DONE", "Signed")
				: step("OFFER_LETTER", "TODO", "HR will share your offer letter"));
		steps.add(docStep("ID_ADDRESS_PROOF", docs, VerificationDocType.ID_PROOF, VerificationDocType.ADDRESS_PROOF));
		steps.add(docStep("POLICE_VERIFICATION", docs, VerificationDocType.BACKGROUND_CHECK));
		if (employment.isBankVerified()) {
			steps.add(step("BANK_ACCOUNT", "DONE", NannyAppSupport.maskAccount(employment.getBankAccountNumber())));
		} else if (employment.getBankAccountNumber() != null) {
			steps.add(step("BANK_ACCOUNT", "IN_REVIEW", "HR is checking your account"));
		} else {
			steps.add(step("BANK_ACCOUNT", "TODO", "Add your salary account"));
		}
		steps.add(employment.getPfUan() != null && employment.getEsiNumber() != null
				? step("PF_ESI", "DONE", "UAN " + employment.getPfUan())
				: step("PF_ESI", "IN_REVIEW", "HR is setting this up"));
		steps.add(trainingStep(nanny, sessions));
		steps.add(employment.getUniformIssuedAt() != null
				? step("UNIFORM_ID_CARD", "DONE", "Collected")
				: step("UNIFORM_ID_CARD", "TODO", "Collect from the centre"));

		int done = (int) steps.stream().filter(s -> s.status().equals("DONE")).count();
		return new OnboardingResponse(startDate(nanny, employment), done, steps.size(), steps,
				hrContactName, hrContactHours, hrContactPhone);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DocumentResponse> documents(Long userId) {
		Nanny nanny = support.nanny(userId);
		return nannyVerificationRepository.findByNannyId(nanny.getId()).stream()
				// REFERENCE is legacy (replaced by nanny_reference), never a document the nanny uploads.
				.filter(v -> v.getType() != VerificationDocType.REFERENCE)
				.sorted(Comparator.comparing(NannyVerification::getCreatedAt))
				.map(v -> new DocumentResponse(String.valueOf(v.getId()), v.getType().name(), documentStatus(v.getStatus()),
						s3PhotoUrlService.presign(v.getS3Key())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public BankAccountResponse bankAccount(Long userId) {
		Nanny nanny = support.nanny(userId);
		return toBank(nanny, support.employment(nanny));
	}

	@Override
	@Transactional
	public BankAccountResponse saveBankAccount(Long userId, BankAccountRequest request) {
		Nanny nanny = support.nanny(userId);
		NannyEmployment employment = support.employment(nanny);
		boolean changed = !request.accountNumber().equals(employment.getBankAccountNumber())
				|| !request.ifsc().equals(employment.getBankIfsc());
		employment.setBankAccountNumber(request.accountNumber());
		employment.setBankIfsc(request.ifsc());
		if (changed) {
			// A new account always goes back to HR for verification before salary is paid into it.
			employment.setBankVerified(false);
		}
		if (employment.getBankAccountHolder() == null) {
			employment.setBankAccountHolder(fullName(nanny));
		}
		return toBank(nanny, support.save(employment));
	}

	@Override
	@Transactional(readOnly = true)
	public StatutoryResponse statutory(Long userId) {
		NannyEmployment employment = support.employment(support.nanny(userId));
		boolean active = employment.getPfUan() != null && employment.getEsiNumber() != null;
		return new StatutoryResponse(employment.getPfUan(), employment.getEsiNumber(), active ? "ACTIVE" : "IN_PROGRESS");
	}

	@Override
	@Transactional(readOnly = true)
	public List<TrainingResponse> trainings(Long userId) {
		Nanny nanny = support.nanny(userId);
		return nannyTrainingSessionRepository.findByNannyIdOrderByStartDateAsc(nanny.getId()).stream()
				.map(t -> new TrainingResponse(String.valueOf(t.getId()), t.getTitle(), t.getStartDate(), t.getLocation(),
						t.getDurationDays(), t.isCompleted()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public EmergencyContactResponse emergencyContact(Long userId) {
		return toContact(support.employment(support.nanny(userId)));
	}

	@Override
	@Transactional
	public EmergencyContactResponse saveEmergencyContact(Long userId, EmergencyContactRequest request) {
		NannyEmployment employment = support.employment(support.nanny(userId));
		employment.setEmergencyName(request.name().trim());
		employment.setEmergencyRelation(request.relation().trim());
		employment.setEmergencyPhone(request.phone().trim());
		return toContact(support.save(employment));
	}

	private NannyMeResponse toMe(Nanny nanny, NannyEmployment employment) {
		String employeeId = employment.getEmployeeCode() != null ? employment.getEmployeeCode()
				: String.format("EKN-%04d", nanny.getId());
		return new NannyMeResponse(nanny.getId(), employeeId, fullName(nanny), Objects.toString(nanny.getUser().getPhone(), ""),
				Objects.requireNonNullElse(employment.getManagerName(), defaultManagerName),
				Objects.requireNonNullElse(employment.getManagerPhone(), defaultManagerPhone),
				employmentStatus(nanny, employment), startDate(nanny, employment), employment.getAppLanguage(),
				rating(nanny), onTimePercent(nanny), trainingsCompleted(nanny), firstAidDue(nanny));
	}

	/** Explicit HR status wins; otherwise derived from verification, so already-approved nannies can use the app without an HR backfill. */
	static String employmentStatus(Nanny nanny, NannyEmployment employment) {
		if (employment.getEmploymentStatus() != null) {
			return employment.getEmploymentStatus();
		}
		NannyVerificationStatus status = nanny.getOverallVerificationStatus();
		if (status == NannyVerificationStatus.APPROVED) {
			return "ACTIVE";
		}
		if (status == NannyVerificationStatus.SUSPENDED || status == NannyVerificationStatus.BANNED) {
			return "SUSPENDED";
		}
		return "ONBOARDING";
	}

	private static LocalDate startDate(Nanny nanny, NannyEmployment employment) {
		return employment.getStartDate() != null ? employment.getStartDate() : NannyAppSupport.istDate(nanny.getCreatedAt());
	}

	private static String fullName(Nanny nanny) {
		return (nanny.getFirstName() + " " + Objects.toString(nanny.getLastName(), "")).trim();
	}

	private double rating(Nanny nanny) {
		List<Object[]> rows = reviewRepository.findRatingAggregate(nanny.getId());
		if (rows.isEmpty() || rows.get(0)[0] == null) {
			return 0;
		}
		return Math.round(((Number) rows.get(0)[0]).doubleValue() * 10) / 10.0;
	}

	private int onTimePercent(Nanny nanny) {
		Instant to = Instant.now();
		Instant from = to.minus(ON_TIME_LOOKBACK_DAYS, ChronoUnit.DAYS);
		List<Booking> checkedIn = bookingRepository.findByNannyIdStartingBetween(nanny.getId(),
				List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED), from, to).stream()
				.filter(b -> b.getCheckedInAt() != null)
				.toList();
		if (checkedIn.isEmpty()) {
			return 100;
		}
		long onTime = checkedIn.stream().filter(NannyAppProfileServiceImpl::checkedInOnTime).count();
		return (int) Math.round(onTime * 100.0 / checkedIn.size());
	}

	static boolean checkedInOnTime(Booking booking) {
		return !booking.getCheckedInAt().isAfter(booking.getStartTime().plus(ON_TIME_GRACE_MINUTES, ChronoUnit.MINUTES));
	}

	private int trainingsCompleted(Nanny nanny) {
		long sessions = nannyTrainingSessionRepository.findByNannyIdOrderByStartDateAsc(nanny.getId()).stream()
				.filter(NannyTrainingSession::isCompleted).count();
		return (int) sessions + (nannyTrainingAttemptRepository.existsByNannyIdAndPassedTrue(nanny.getId()) ? 1 : 0);
	}

	private LocalDate firstAidDue(Nanny nanny) {
		return nannyVerificationRepository.findByNannyId(nanny.getId()).stream()
				.filter(v -> v.getType() == VerificationDocType.FIRST_AID && v.getExpiryDate() != null)
				.map(NannyVerification::getExpiryDate)
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	private OnboardingStepResponse trainingStep(Nanny nanny, List<NannyTrainingSession> sessions) {
		boolean quizPassed = nannyTrainingAttemptRepository.existsByNannyIdAndPassedTrue(nanny.getId());
		boolean sessionDone = sessions.stream().anyMatch(NannyTrainingSession::isCompleted);
		if (sessionDone || (quizPassed && sessions.isEmpty())) {
			return step("TRAINING", "DONE", "Completed");
		}
		return sessions.stream().filter(s -> !s.isCompleted()).findFirst()
				.map(s -> step("TRAINING", "IN_REVIEW", s.getTitle() + " · " + s.getStartDate() + " · " + s.getLocation()))
				.orElseGet(() -> step("TRAINING", "TODO", "HR will schedule your training"));
	}

	private static OnboardingStepResponse docStep(String id, List<NannyVerification> docs, VerificationDocType... types) {
		boolean allVerified = true;
		boolean anyRejected = false;
		boolean anyUploaded = false;
		for (VerificationDocType type : types) {
			NannyVerification latest = docs.stream().filter(d -> d.getType() == type)
					.max(Comparator.comparing(NannyVerification::getCreatedAt)).orElse(null);
			if (latest == null) {
				allVerified = false;
				continue;
			}
			anyUploaded = true;
			allVerified &= latest.getStatus() == VerificationRecordStatus.VERIFIED;
			anyRejected |= latest.getStatus() == VerificationRecordStatus.REJECTED;
		}
		if (allVerified) {
			return step(id, "DONE", "Verified");
		}
		if (anyRejected) {
			return step(id, "TODO", "Rejected - please upload again");
		}
		return anyUploaded ? step(id, "IN_REVIEW", "Being checked") : step(id, "TODO", "Upload in the verification flow");
	}

	private static OnboardingStepResponse step(String id, String status, String detail) {
		return new OnboardingStepResponse(id, status, detail);
	}

	private static String documentStatus(VerificationRecordStatus status) {
		return switch (status) {
			case VERIFIED -> "VERIFIED";
			case REJECTED -> "REJECTED";
			case PENDING -> "IN_REVIEW";
		};
	}

	private static BankAccountResponse toBank(Nanny nanny, NannyEmployment employment) {
		return new BankAccountResponse(NannyAppSupport.maskAccount(employment.getBankAccountNumber()),
				Objects.toString(employment.getBankIfsc(), ""),
				employment.getBankAccountHolder() != null ? employment.getBankAccountHolder() : fullName(nanny),
				employment.isBankVerified());
	}

	private static EmergencyContactResponse toContact(NannyEmployment employment) {
		return new EmergencyContactResponse(Objects.toString(employment.getEmergencyName(), ""),
				Objects.toString(employment.getEmergencyRelation(), ""), Objects.toString(employment.getEmergencyPhone(), ""));
	}

}

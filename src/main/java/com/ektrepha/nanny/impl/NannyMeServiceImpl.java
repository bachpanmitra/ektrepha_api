package com.ektrepha.nanny.impl;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.nanny.dto.request.NannyEmergencyContactRequest;
import com.ektrepha.nanny.dto.request.NannyMeUpdateRequest;
import com.ektrepha.nanny.dto.response.NannyEmergencyContactResponse;
import com.ektrepha.nanny.dto.response.NannyMeResponse;
import com.ektrepha.nanny.service.NannyMeService;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyTrainingAttemptRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NannyMeServiceImpl implements NannyMeService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	private final NannyRepository nannyRepository;
	private final ReviewRepository reviewRepository;
	private final NannyTrainingAttemptRepository nannyTrainingAttemptRepository;

	@Override
	@Transactional(readOnly = true)
	public NannyMeResponse getMe(Long userId) {
		return toResponse(resolveCallerNanny(userId));
	}

	@Override
	@Transactional
	public NannyMeResponse updateMe(Long userId, NannyMeUpdateRequest request) {
		Nanny nanny = resolveCallerNanny(userId);
		if (request.language() != null) {
			nanny.setLanguage(request.language());
		}
		return toResponse(nannyRepository.save(nanny));
	}

	@Override
	@Transactional(readOnly = true)
	public NannyEmergencyContactResponse getEmergencyContact(Long userId) {
		Nanny nanny = resolveCallerNanny(userId);
		return new NannyEmergencyContactResponse(nanny.getEmergencyContactName(), nanny.getEmergencyContactRelation(),
				nanny.getEmergencyContactPhone());
	}

	@Override
	@Transactional
	public NannyEmergencyContactResponse saveEmergencyContact(Long userId, NannyEmergencyContactRequest request) {
		Nanny nanny = resolveCallerNanny(userId);
		nanny.setEmergencyContactName(request.name());
		nanny.setEmergencyContactRelation(request.relation());
		nanny.setEmergencyContactPhone(request.phone());
		nannyRepository.save(nanny);
		return new NannyEmergencyContactResponse(request.name(), request.relation(), request.phone());
	}

	private NannyMeResponse toResponse(Nanny nanny) {
		CareManager careManager = nanny.getCareManager();
		List<Object[]> ratingAggregate = reviewRepository.findRatingAggregate(nanny.getId());
		Double avgRating = ratingAggregate.isEmpty() ? null : (Double) ratingAggregate.get(0)[0];
		long trainingsCompleted = nannyTrainingAttemptRepository.countByNannyIdAndPassedTrue(nanny.getId());

		return new NannyMeResponse(
				nanny.getId(),
				nanny.getPublicId(),
				fullName(nanny.getFirstName(), nanny.getLastName()),
				nanny.getUser().getPhone(),
				careManager == null ? "" : careManager.getName(),
				careManager == null ? "" : careManager.getPhone(),
				resolveEmploymentStatus(nanny),
				nanny.getCreatedAt().atZone(INDIA_ZONE).toLocalDate(),
				nanny.getLanguage(),
				avgRating == null ? 0 : avgRating,
				0, // onTimePercent - no punctuality metric computed anywhere yet, honest zero rather than a guess
				(int) trainingsCompleted,
				null // firstAidRefresherDueDate - no refresher-cadence concept exists yet
		);
	}

	private String resolveEmploymentStatus(Nanny nanny) {
		if (nanny.getOverallVerificationStatus() != NannyVerificationStatus.APPROVED) {
			return "ONBOARDING";
		}
		return nanny.getStatus() == NannyOperationalStatus.SUSPENDED ? "SUSPENDED" : "ACTIVE";
	}

	private String fullName(String firstName, String lastName) {
		return (firstName == null ? "" : firstName) + (lastName == null ? "" : " " + lastName);
	}

	private Nanny resolveCallerNanny(Long userId) {
		return nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile found for this account"));
	}

}

package com.ektrepha.nanny.impl;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.nanny.dto.response.NannyPublicProfileResponse;
import com.ektrepha.nanny.dto.response.NannyReviewListResponse;
import com.ektrepha.nanny.dto.response.PublicReviewResponse;
import com.ektrepha.nanny.dto.response.VerificationItem;
import com.ektrepha.nanny.dto.response.VerificationSummaryResponse;
import com.ektrepha.nanny.service.NannyProfileService;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannySkillLanguageRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NannyProfileServiceImpl implements NannyProfileService {

	// S2 labels/descriptions — never derived from the raw enum name in the client, so copy changes
	// stay a server-side, one-place edit.
	private static final Map<VerificationDocType, String> LABELS = Map.of(
			VerificationDocType.ID_PROOF, "Government ID",
			VerificationDocType.BACKGROUND_CHECK, "Background check",
			VerificationDocType.EDUCATION, "Education",
			VerificationDocType.FIRST_AID, "First aid certification",
			VerificationDocType.REFERENCE, "Reference check");

	private static final Map<VerificationDocType, String> DESCRIPTIONS = Map.of(
			VerificationDocType.ID_PROOF, "Government-issued photo ID verified",
			VerificationDocType.BACKGROUND_CHECK, "Criminal background check completed",
			VerificationDocType.EDUCATION, "Educational qualifications verified",
			VerificationDocType.FIRST_AID, "First aid / CPR certification on file",
			VerificationDocType.REFERENCE, "Prior employer or personal reference checked");

	private final NannyRepository nannyRepository;
	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannySkillLanguageRepository nannySkillLanguageRepository;
	private final ReviewRepository reviewRepository;

	@Override
	@Transactional(readOnly = true)
	public NannyPublicProfileResponse getProfile(Long nannyId) {
		Nanny nanny = resolveNanny(nannyId);
		Object[] aggregate = firstAggregateRow(nannyId);
		Double ratingAvg = aggregate == null ? null : (Double) aggregate[0];
		Long reviewCount = aggregate == null ? null : (Long) aggregate[1];

		List<PublicReviewResponse> recentReviews = reviewRepository
				.findByNannyIdOrderByCreatedAtDesc(nannyId, PageRequest.of(0, 5))
				.map(this::toPublicReview)
				.getContent();

		return new NannyPublicProfileResponse(
				nanny.getId(),
				nanny.getFirstName(),
				nanny.getLastName(),
				nanny.getProfilePhotoS3Key(),
				nanny.getBio(),
				nanny.getYearsExperience(),
				nanny.getEducationLevel(),
				nanny.getHourlyRate(),
				ratingAvg,
				reviewCount == null ? 0 : reviewCount.intValue(),
				nanny.getOverallVerificationStatus() == NannyVerificationStatus.VERIFIED,
				nannySkillLanguageRepository.findSkillNames(nannyId),
				nannySkillLanguageRepository.findLanguageNames(nannyId),
				recentReviews);
	}

	@Override
	@Transactional(readOnly = true)
	public VerificationSummaryResponse getVerification(Long nannyId) {
		Nanny nanny = resolveNanny(nannyId);
		Map<VerificationDocType, NannyVerification> latestByType = latestRecordPerType(nannyVerificationRepository.findByNannyId(nannyId));

		List<VerificationItem> items = List.of(VerificationDocType.values()).stream()
				.map(type -> toItem(type, latestByType.get(type)))
				.toList();
		int completedCount = (int) items.stream().filter(i -> "VERIFIED".equals(i.status())).count();

		return new VerificationSummaryResponse(
				nanny.getId(),
				nanny.getFirstName(),
				nanny.getLastName(),
				nanny.getProfilePhotoS3Key(),
				nanny.getOverallVerificationStatus().name(),
				completedCount,
				items.size(),
				items);
	}

	@Override
	@Transactional(readOnly = true)
	public NannyReviewListResponse listReviews(Long nannyId, int page, int pageSize) {
		resolveNanny(nannyId);
		Page<Review> reviews = reviewRepository.findByNannyIdOrderByCreatedAtDesc(nannyId, PageRequest.of(page, pageSize));
		Object[] aggregate = firstAggregateRow(nannyId);
		Double ratingAvg = aggregate == null ? null : (Double) aggregate[0];
		Long reviewCount = aggregate == null ? 0L : (Long) aggregate[1];

		return new NannyReviewListResponse(
				reviews.getContent().stream().map(this::toPublicReview).toList(),
				reviews.getNumber(),
				reviews.getSize(),
				reviews.getTotalElements(),
				reviews.getTotalPages(),
				ratingAvg,
				reviewCount.intValue());
	}

	private Object[] firstAggregateRow(Long nannyId) {
		List<Object[]> rows = reviewRepository.findRatingAggregate(nannyId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private Nanny resolveNanny(Long nannyId) {
		return nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));
	}

	// Same "latest row per type" reduction as NannyVerificationServiceImpl's rollup recompute —
	// the schema allows multiple rows per (nanny, type) (a rejected submission can be resubmitted).
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

	private VerificationItem toItem(VerificationDocType type, NannyVerification record) {
		VerificationRecordStatus status = record == null ? VerificationRecordStatus.PENDING : record.getStatus();
		Instant verifiedAt = record != null && status == VerificationRecordStatus.VERIFIED ? record.getReviewedAt() : null;
		return new VerificationItem(type.name(), LABELS.get(type), DESCRIPTIONS.get(type), status.name(), verifiedAt);
	}

	private PublicReviewResponse toPublicReview(Review review) {
		return new PublicReviewResponse(review.getId(), displayName(review.getParent()), review.getRating().intValue(), review.getComment(), review.getCreatedAt());
	}

	// "Anjali M." — never the reviewer's full identity.
	private String displayName(Parent parent) {
		String lastInitial = parent.getLastName() == null || parent.getLastName().isBlank()
				? ""
				: " " + parent.getLastName().charAt(0) + ".";
		return parent.getFirstName() + lastInitial;
	}

}

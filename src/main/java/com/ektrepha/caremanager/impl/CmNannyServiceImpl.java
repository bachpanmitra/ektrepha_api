package com.ektrepha.caremanager.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.AccessLogRecorder;
import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CmPhoneMasker;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.dto.request.FlagReviewRequest;
import com.ektrepha.caremanager.dto.response.NannyDetailResponse;
import com.ektrepha.caremanager.dto.response.NannyListItemResponse;
import com.ektrepha.caremanager.dto.response.NannyListPageResponse;
import com.ektrepha.caremanager.dto.response.NannyVerificationSummaryResponse;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmBookingMapper;
import com.ektrepha.caremanager.service.CmNannyMapper;
import com.ektrepha.caremanager.service.CmNannyService;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.AccessLogAction;
import com.ektrepha.model.AccessLogRecordType;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyInterview;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.NannyReference;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.User;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.NannyInterviewRepository;
import com.ektrepha.repository.NannyReferenceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.storage.GcsPhotoUrlService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmNannyServiceImpl implements CmNannyService {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final NannyRepository nannyRepository;
	private final NannyVerificationRepository nannyVerificationRepository;
	private final NannyReferenceRepository nannyReferenceRepository;
	private final NannyInterviewRepository nannyInterviewRepository;
	private final BookingRepository bookingRepository;
	private final ComplaintRepository complaintRepository;
	private final CmNannyMapper cmNannyMapper;
	private final CmBookingMapper cmBookingMapper;
	private final GcsPhotoUrlService gcsPhotoUrlService;
	private final CareManagerAccessService careManagerAccessService;
	private final AccessLogRecorder accessLogRecorder;

	@Override
	@Transactional(readOnly = true)
	public NannyListPageResponse listMyNannies(com.ektrepha.model.CareManager caller, NannyOperationalStatus status, String cursor,
			Integer limit) {
		List<Nanny> all = status == null ? nannyRepository.findByCareManagerId(caller.getId())
				: nannyRepository.findByCareManagerIdAndStatus(caller.getId(), status);
		List<Nanny> sorted = all.stream().sorted(Comparator.comparing(Nanny::getId)).toList();

		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		int startIndex = 0;
		if (decoded != null) {
			for (int i = 0; i < sorted.size(); i++) {
				if (sorted.get(i).getId().equals(decoded.id())) {
					startIndex = i + 1;
					break;
				}
			}
		}
		List<Nanny> page = sorted.subList(Math.min(startIndex, sorted.size()), Math.min(startIndex + pageSize, sorted.size()));
		String nextCursor = startIndex + pageSize < sorted.size()
				? CursorCodec.encode("page", page.get(page.size() - 1).getId())
				: null;

		return new NannyListPageResponse(page.stream().map(cmNannyMapper::map).toList(), nextCursor);
	}

	@Override
	@Transactional(readOnly = true)
	public NannyListPageResponse searchNannies(String q, String cursor, Integer limit) {
		String namePattern = "%" + q.toLowerCase() + "%";
		String phonePattern = "%" + q;
		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Long afterId = decoded == null ? null : decoded.id();

		List<Nanny> rows = nannyRepository.search(namePattern, phonePattern, afterId, PageRequest.of(0, pageSize + 1));
		boolean hasMore = rows.size() > pageSize;
		List<Nanny> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore ? CursorCodec.encode("page", page.get(page.size() - 1).getId()) : null;
		return new NannyListPageResponse(page.stream().map(cmNannyMapper::map).toList(), nextCursor);
	}

	@Override
	@Transactional
	public NannyDetailResponse getNannyDetail(CmCaller caller, User actor, String nannyPublicId) {
		Nanny nanny = nannyRepository.findByPublicId(nannyPublicId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyPublicId));
		NannyListItemResponse base = cmNannyMapper.map(nanny);

		Long ownerCmId = nanny.getCareManager() == null ? null : nanny.getCareManager().getId();
		boolean isMyNanny = careManagerAccessService.isMyRecord(caller, ownerCmId);
		boolean canEdit = careManagerAccessService.canEdit(caller, ownerCmId);

		NannyVerificationSummaryResponse verification = buildVerificationSummary(nanny);
		List<com.ektrepha.caremanager.dto.response.PersonRefResponse> families = bookingRepository
				.findDistinctParentsByNannyId(nanny.getId()).stream().map(CmPersonRefMapper::of).toList();
		List<com.ektrepha.caremanager.dto.response.BookingResponse> schedule = bookingRepository
				.findByNannyIdOrderByStartTimeDesc(nanny.getId(), PageRequest.of(0, 20)).stream().map(cmBookingMapper::map).toList();
		List<com.ektrepha.caremanager.dto.response.ComplaintResponse> complaints = complaintRepository
				.findByNannyId(nanny.getId()).stream().map(this::toComplaintResponse).toList();

		accessLogRecorder.record(actor, AccessLogRecordType.NANNY, nanny.getId(), AccessLogAction.VIEW, null);

		return new NannyDetailResponse(
				base.id(), base.name(), base.phoneMasked(), base.status(), base.avgRating(), base.lowRatingsCount(),
				base.familiesCount(), base.careManager(),
				nanny.getUser().getPhone(),
				null,
				List.of(),
				nanny.getYearsExperience() == null ? null : nanny.getYearsExperience().doubleValue(),
				nanny.getCreatedAt(),
				isMyNanny,
				canEdit,
				verification,
				families,
				schedule,
				complaints,
				// "Family brief for your visit" (Nanny, on booking assigned) isn't wired yet — no
				// tracking table exists for when a brief was actually sent. Empty rather than fabricated.
				List.of());
	}

	@Override
	@Transactional
	public NannyListItemResponse reviewFlag(CmCaller caller, User actor, String nannyPublicId, FlagReviewRequest request) {
		Nanny nanny = nannyRepository.findByPublicId(nannyPublicId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyPublicId));
		Long ownerCmId = nanny.getCareManager() == null ? null : nanny.getCareManager().getId();
		careManagerAccessService.requireWrite(caller, ownerCmId, "this nanny");

		nanny.setStatus(request.decision() == FlagReviewRequest.Decision.CLEAR
				? NannyOperationalStatus.ACTIVE : NannyOperationalStatus.SUSPENDED);
		nannyRepository.save(nanny);

		accessLogRecorder.record(actor, AccessLogRecordType.NANNY, nanny.getId(), AccessLogAction.EDIT, "flag");
		return cmNannyMapper.map(nanny);
	}

	private NannyVerificationSummaryResponse buildVerificationSummary(Nanny nanny) {
		List<NannyVerification> records = nannyVerificationRepository.findByNannyId(nanny.getId());
		Instant idVerifiedAt = latestVerifiedAt(records, VerificationDocType.ID_PROOF);
		Instant policeVerifiedAt = latestVerifiedAt(records, VerificationDocType.BACKGROUND_CHECK);

		List<NannyReference> references = nannyReferenceRepository.findByNannyId(nanny.getId());
		Instant referencesCheckedAt = references.stream()
				.filter(r -> r.getStatus() == VerificationRecordStatus.VERIFIED)
				.map(NannyReference::getVerifiedAt)
				.filter(java.util.Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);

		List<NannyInterview> interviews = nannyInterviewRepository.findByNannyIdOrderByCreatedAtDesc(nanny.getId());
		Instant interviewedAt = interviews.stream()
				.map(NannyInterview::getConductedAt)
				.filter(java.util.Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);

		List<NannyVerificationSummaryResponse.DocumentResponse> documents = records.stream()
				.map(r -> new NannyVerificationSummaryResponse.DocumentResponse(mapDocType(r.getType()), gcsPhotoUrlService.presign(r.getS3Key())))
				.toList();

		return new NannyVerificationSummaryResponse(idVerifiedAt, policeVerifiedAt, referencesCheckedAt, interviewedAt, documents);
	}

	private Instant latestVerifiedAt(List<NannyVerification> records, VerificationDocType type) {
		return records.stream()
				.filter(r -> r.getType() == type && r.getStatus() == VerificationRecordStatus.VERIFIED)
				.map(NannyVerification::getReviewedAt)
				.filter(java.util.Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	private String mapDocType(VerificationDocType type) {
		return switch (type) {
			case ID_PROOF -> "ID";
			case BACKGROUND_CHECK -> "POLICE_CERTIFICATE";
			case REFERENCE -> "REFERENCE";
			default -> "OTHER";
		};
	}

	private com.ektrepha.caremanager.dto.response.ComplaintResponse toComplaintResponse(com.ektrepha.model.Complaint c) {
		return new com.ektrepha.caremanager.dto.response.ComplaintResponse(
				c.getPublicId(), CmPersonRefMapper.of(c.getParent()),
				c.getNanny() == null ? null : CmPersonRefMapper.of(c.getNanny()),
				c.getBooking() == null ? null : c.getBooking().getPublicId(),
				c.getCategory(), c.getDescription(), c.getStatus(), CmPersonRefMapper.of(c.getCareManager()),
				c.getEscalatedTo() == null ? null : CmPersonRefMapper.of(c.getEscalatedTo()),
				c.isTrialRefundIssued(), c.getResolution(), c.getRaisedAt(), c.getClosedAt());
	}

	private int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

}

package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.dto.request.RaiseComplaintRequest;
import com.ektrepha.caremanager.dto.request.UpdateComplaintRequest;
import com.ektrepha.caremanager.dto.response.ComplaintPageResponse;
import com.ektrepha.caremanager.dto.response.ComplaintResponse;
import com.ektrepha.caremanager.event.ComplaintRaisedPayload;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmComplaintService;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.Complaint;
import com.ektrepha.model.ComplaintCategory;
import com.ektrepha.model.ComplaintStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmDutyShiftRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmComplaintServiceImpl implements CmComplaintService {

	private static final Duration TRIAL_REFUND_WINDOW = Duration.ofDays(5);
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final ComplaintRepository complaintRepository;
	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;
	private final CareManagerRepository careManagerRepository;
	private final CmDutyShiftRepository cmDutyShiftRepository;
	private final CareManagerAccessService careManagerAccessService;
	private final CmNotificationService cmNotificationService;
	private final OutboxEventWriter outboxEventWriter;
	private final AppProperties appProperties;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public ComplaintPageResponse list(CmCaller caller, ComplaintStatus status, boolean mine, String cursor, Integer limit) {
		Long careManagerFilter = mine ? caller.careManagerId() : null;
		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Instant beforeRaisedAt = decoded == null ? null : Instant.parse(decoded.sortKey());
		Long beforeId = decoded == null ? null : decoded.id();

		List<Complaint> rows = complaintRepository.search(careManagerFilter, status, beforeRaisedAt, beforeId,
				PageRequest.of(0, pageSize + 1));
		boolean hasMore = rows.size() > pageSize;
		List<Complaint> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore
				? CursorCodec.encode(page.get(page.size() - 1).getRaisedAt().toString(), page.get(page.size() - 1).getId())
				: null;
		return new ComplaintPageResponse(page.stream().map(this::toResponse).toList(), nextCursor);
	}

	@Override
	@Transactional
	public ComplaintResponse raise(CmCaller caller, RaiseComplaintRequest request) {
		Parent parent = parentRepository.findByPublicId(request.parentId())
				.orElseThrow(() -> new com.ektrepha.exception.ParentNotFoundException("No parent with id " + request.parentId()));
		CareManager owner = parent.getCareManager();
		if (owner == null) {
			throw new CmResourceNotFoundException("This parent has no Care Manager assigned yet");
		}
		Nanny nanny = request.nannyId() == null ? null : nannyRepository.findByPublicId(request.nannyId())
				.orElseThrow(() -> new com.ektrepha.exception.NannyNotFoundException("No nanny with id " + request.nannyId()));
		Booking booking = request.bookingId() == null ? null : bookingRepository.findByPublicId(request.bookingId())
				.orElseThrow(() -> new com.ektrepha.exception.BookingNotFoundException("No booking with id " + request.bookingId()));

		boolean safety = request.category() == ComplaintCategory.SAFETY;
		Complaint complaint = Complaint.builder()
				.parent(parent)
				.nanny(nanny)
				.booking(booking)
				.careManager(owner)
				.category(request.category())
				.description(request.description())
				.build();
		if (safety) {
			complaint.setStatus(ComplaintStatus.ESCALATED);
			complaint.setEscalatedAt(Instant.now(clock));
			String seniorManagerId = appProperties.careManager().seniorManagerCareManagerId();
			if (seniorManagerId != null && !seniorManagerId.isBlank()) {
				careManagerRepository.findByPublicId(seniorManagerId).ifPresent(sm -> complaint.setEscalatedTo(sm.getUser()));
			}
		}
		complaintRepository.save(complaint);

		if (safety && nanny != null) {
			nanny.setStatus(NannyOperationalStatus.SUSPENDED);
			nannyRepository.save(nanny);
		}
		if (safety) {
			List<CmDutyShift> onDuty = cmDutyShiftRepository.findAnyCoveringNow(Instant.now(clock));
			cmNotificationService.alertSafetyEscalation(complaint, onDuty.isEmpty() ? null : onDuty.get(0).getCareManager());
		}

		outboxEventWriter.write("ComplaintRaised",
				new ComplaintRaisedPayload(complaint.getId(), parent.getId(), nanny == null ? null : nanny.getId(), safety));

		return toResponse(complaint);
	}

	@Override
	@Transactional
	public ComplaintResponse update(CmCaller caller, String complaintPublicId, UpdateComplaintRequest request) {
		Complaint complaint = complaintRepository.findByPublicId(complaintPublicId)
				.orElseThrow(() -> new CmResourceNotFoundException("No complaint with id " + complaintPublicId));
		careManagerAccessService.requireWrite(caller, complaint.getCareManager().getId(), "this complaint");

		if (request.status() == ComplaintStatus.CLOSED
				&& (request.resolution() == null || request.resolution().isBlank())
				&& (complaint.getResolution() == null || complaint.getResolution().isBlank())) {
			throw new CmValidationException("resolution is required to close a complaint",
					java.util.Map.of("resolution", "required when status is CLOSED"));
		}

		if (Boolean.TRUE.equals(request.issueTrialRefund())) {
			Booking firstBooking = bookingRepository.findFirstByParentIdOrderByStartTimeAsc(complaint.getParent().getId()).orElse(null);
			boolean withinWindow = firstBooking != null
					&& firstBooking.getStartTime().isAfter(Instant.now(clock).minus(TRIAL_REFUND_WINDOW));
			if (!withinWindow) {
				throw new CmValidationException("Trial refund is only available within 5 days of the first booking",
						java.util.Map.of("issue_trial_refund", "outside the 5-day trial refund window"));
			}
			// Record-only: no payment integration exists in this codebase (cash-on-completion,
			// nothing to refund today) — finance processes the real refund manually, same as every
			// other money-movement outside this app currently.
			complaint.setTrialRefundIssued(true);
		}

		if (request.status() != null) {
			complaint.setStatus(request.status());
			if (request.status() == ComplaintStatus.CLOSED) {
				complaint.setClosedAt(Instant.now(clock));
			}
		}
		if (request.resolution() != null) {
			complaint.setResolution(request.resolution());
		}
		complaintRepository.save(complaint);

		return toResponse(complaint);
	}

	private ComplaintResponse toResponse(Complaint c) {
		return new ComplaintResponse(
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

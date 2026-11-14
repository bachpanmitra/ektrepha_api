package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CmPhoneMasker;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.dto.request.CmLogContactRequest;
import com.ektrepha.caremanager.dto.request.FamilyNotesRequest;
import com.ektrepha.caremanager.dto.response.AssignmentResponse;
import com.ektrepha.caremanager.dto.response.BookingPageResponse;
import com.ektrepha.caremanager.dto.response.ContactPageResponse;
import com.ektrepha.caremanager.dto.response.ContactResponse;
import com.ektrepha.caremanager.dto.response.FamilyNotesResponse;
import com.ektrepha.caremanager.dto.response.ParentDetailResponse;
import com.ektrepha.caremanager.dto.response.ParentListItemResponse;
import com.ektrepha.caremanager.dto.response.ParentListPageResponse;
import com.ektrepha.caremanager.dto.response.PaymentSummaryResponse;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmBookingMapper;
import com.ektrepha.caremanager.service.CmNannyMapper;
import com.ektrepha.caremanager.service.CmParentService;
import com.ektrepha.caremanager.service.ParentStageService;
import com.ektrepha.exception.ParentNotFoundException;
import com.ektrepha.exception.VersionMismatchException;
import com.ektrepha.model.AccessLogAction;
import com.ektrepha.model.AccessLogRecordType;
import com.ektrepha.model.Booking;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmContact;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.CmTaskType;
import com.ektrepha.model.ComplaintStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentNote;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.model.User;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.CmContactRepository;
import com.ektrepha.repository.CmContactRepository.LastContact;
import com.ektrepha.repository.CmTaskRepository;
import com.ektrepha.repository.CmTaskRepository.ParentOpenTaskType;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.ComplaintRepository.ParentComplaintCount;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentNoteRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ReviewRepository;

import com.ektrepha.caremanager.AccessLogRecorder;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmParentServiceImpl implements CmParentService {

	private static final List<ComplaintStatus> OPEN_STATUSES = List.of(ComplaintStatus.OPEN, ComplaintStatus.ESCALATED);
	private static final List<CmTaskType> ISSUE_TASK_TYPES = List.of(CmTaskType.INTRO_CALL, CmTaskType.MONTHLY_CHECKIN);
	private static final Duration LAPSED_AFTER = Duration.ofDays(60);
	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final ParentRepository parentRepository;
	private final ParentTrialRepository parentTrialRepository;
	private final BookingRepository bookingRepository;
	private final CmContactRepository cmContactRepository;
	private final ComplaintRepository complaintRepository;
	private final ReviewRepository reviewRepository;
	private final CmTaskRepository cmTaskRepository;
	private final CmAssignmentRepository cmAssignmentRepository;
	private final ParentNoteRepository parentNoteRepository;
	private final ParentChildRepository parentChildRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final CmBookingMapper cmBookingMapper;
	private final CmNannyMapper cmNannyMapper;
	private final ParentStageService parentStageService;
	private final CareManagerAccessService careManagerAccessService;
	private final AccessLogRecorder accessLogRecorder;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public ParentListPageResponse listMyParents(CareManager caller, ParentStage stageFilter, Boolean hasIssues, String sort,
			String cursor, Integer limit) {
		List<Parent> all = parentRepository.findByCareManagerId(caller.getId());
		if (all.isEmpty()) {
			return new ParentListPageResponse(List.of(), null, 0L);
		}
		List<Long> ids = all.stream().map(Parent::getId).toList();
		Instant now = Instant.now(clock);

		Set<Long> onActiveTrial = new HashSet<>();
		parentTrialRepository.findByParentIdInAndStatus(ids, com.ektrepha.model.TrialStatus.ACTIVE)
				.forEach(t -> onActiveTrial.add(t.getParentId()));

		Map<Long, Booking> latestBookingByParent = new HashMap<>();
		for (Booking b : bookingRepository.findByParentIdInOrderByStartTimeDesc(ids)) {
			latestBookingByParent.putIfAbsent(b.getParent().getId(), b);
		}
		Map<Long, Instant> lastContactByParent = new HashMap<>();
		for (LastContact lc : cmContactRepository.findLastContactByParentIds(ids)) {
			lastContactByParent.put(lc.getParentId(), lc.getLastContactAt());
		}
		Map<Long, Long> openComplaintsByParent = new HashMap<>();
		for (ParentComplaintCount pc : complaintRepository.countOpenByParentIds(ids, OPEN_STATUSES)) {
			openComplaintsByParent.put(pc.getParentId(), pc.getOpenCount());
		}
		Set<Long> parentsWithLowRating = new HashSet<>(reviewRepository.findParentIdsWithLowRating(ids, (short) 3));
		Map<Long, Set<CmTaskType>> openTaskTypesByParent = new HashMap<>();
		for (ParentOpenTaskType row : cmTaskRepository.findOpenTaskTypesByParentIds(ids, ISSUE_TASK_TYPES)) {
			openTaskTypesByParent.computeIfAbsent(row.getParentId(), k -> new HashSet<>()).add(row.getType());
		}

		record Enriched(Parent parent, ParentStage stage, Integer trialDay, Instant lastContactAt, List<String> issues) {
		}
		List<Enriched> enriched = new ArrayList<>();
		for (Parent p : all) {
			boolean trial = onActiveTrial.contains(p.getId());
			Booking latest = latestBookingByParent.get(p.getId());
			ParentStage stage = trial ? ParentStage.TRIAL
					: latest == null ? ParentStage.NEW
							: latest.getStartTime().isBefore(now.minus(LAPSED_AFTER)) ? ParentStage.LAPSED : ParentStage.REGULAR;
			Integer trialDay = trial ? parentStageService.resolveTrialDay(p) : null;

			List<String> issues = new ArrayList<>();
			long openComplaints = openComplaintsByParent.getOrDefault(p.getId(), 0L);
			if (openComplaints == 1) {
				issues.add("1 complaint");
			} else if (openComplaints > 1) {
				issues.add(openComplaints + " complaints");
			}
			if (parentsWithLowRating.contains(p.getId())) {
				issues.add("low rating");
			}
			Set<CmTaskType> openTypes = openTaskTypesByParent.getOrDefault(p.getId(), Set.of());
			if (openTypes.contains(CmTaskType.INTRO_CALL)) {
				issues.add("intro call due");
			}
			if (openTypes.contains(CmTaskType.MONTHLY_CHECKIN)) {
				issues.add("monthly check-in due");
			}

			enriched.add(new Enriched(p, stage, trialDay, lastContactByParent.get(p.getId()), issues));
		}

		List<Enriched> filtered = enriched.stream()
				.filter(e -> stageFilter == null || e.stage() == stageFilter)
				.filter(e -> hasIssues == null || hasIssues == !e.issues().isEmpty())
				.toList();

		Comparator<Enriched> comparator = switch (sort == null ? "last_contact" : sort) {
			case "name" -> Comparator.comparing(e -> displayName(e.parent()));
			case "stage" -> Comparator.comparing(e -> e.stage().name());
			default -> Comparator.comparing(Enriched::lastContactAt, Comparator.nullsFirst(Comparator.naturalOrder()));
		};
		List<Enriched> sorted = filtered.stream().sorted(comparator.thenComparing(e -> e.parent().getId())).toList();

		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		int startIndex = 0;
		if (decoded != null) {
			for (int i = 0; i < sorted.size(); i++) {
				if (sorted.get(i).parent().getId().equals(decoded.id())) {
					startIndex = i + 1;
					break;
				}
			}
		}
		List<Enriched> page = sorted.subList(Math.min(startIndex, sorted.size()),
				Math.min(startIndex + pageSize, sorted.size()));
		String nextCursor = startIndex + pageSize < sorted.size()
				? CursorCodec.encode("page", page.get(page.size() - 1).parent().getId())
				: null;

		List<ParentListItemResponse> items = page.stream().map(e -> toListItem(e.parent(), e.stage(), e.trialDay(),
				e.lastContactAt(), e.issues(), latestBookingByParent.get(e.parent().getId()))).toList();
		return new ParentListPageResponse(items, nextCursor, (long) filtered.size());
	}

	@Override
	@Transactional(readOnly = true)
	public ParentListPageResponse searchParents(String q, String cursor, Integer limit) {
		String namePattern = "%" + q.toLowerCase() + "%";
		String phonePattern = "%" + q;
		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Long afterId = decoded == null ? null : decoded.id();

		List<Parent> rows = parentRepository.search(namePattern, phonePattern, afterId, PageRequest.of(0, pageSize + 1));
		boolean hasMore = rows.size() > pageSize;
		List<Parent> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore ? CursorCodec.encode("page", page.get(page.size() - 1).getId()) : null;

		List<ParentListItemResponse> items = page.stream().map(p -> {
			Booking latest = bookingRepository.findFirstByParentIdOrderByStartTimeDesc(p.getId()).orElse(null);
			ParentStage stage = parentStageService.resolveStage(p);
			Integer trialDay = parentStageService.resolveTrialDay(p);
			return toListItem(p, stage, trialDay, null, List.of(), latest);
		}).toList();
		return new ParentListPageResponse(items, nextCursor, null);
	}

	@Override
	@Transactional
	public ParentDetailResponse getParentDetail(CmCaller caller, User actor, String parentPublicId) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));

		Long ownerCmId = parent.getCareManager() == null ? null : parent.getCareManager().getId();
		boolean isMyFamily = careManagerAccessService.isMyRecord(caller, ownerCmId);
		boolean canEdit = careManagerAccessService.canEdit(caller, ownerCmId);

		ParentStage stage = parentStageService.resolveStage(parent);
		Integer trialDay = parentStageService.resolveTrialDay(parent);

		List<ParentDetailResponse.ChildResponse> children = parentChildRepository.findByIdParentId(parent.getId()).stream()
				.map(pc -> new ParentDetailResponse.ChildResponse(
						(pc.getChild().getFirstName() + " " + (pc.getChild().getLastName() == null ? "" : pc.getChild().getLastName())).trim(),
						ageYears(pc.getChild().getDob())))
				.toList();

		Booking latestBooking = bookingRepository.findFirstByParentIdOrderByStartTimeDesc(parent.getId()).orElse(null);
		var currentNanny = latestBooking == null || latestBooking.getNanny() == null ? null
				: cmNannyMapper.map(latestBooking.getNanny());

		ParentNote note = parentNoteRepository.findById(parent.getId()).orElse(null);
		FamilyNotesResponse notes = toFamilyNotes(note);

		List<com.ektrepha.caremanager.dto.response.ComplaintResponse> openComplaints = complaintRepository
				.findByParentIdOrderByRaisedAtDesc(parent.getId()).stream()
				.filter(c -> OPEN_STATUSES.contains(c.getStatus()))
				.map(this::toComplaintResponse)
				.toList();

		Instant now = Instant.now(clock);
		List<com.ektrepha.caremanager.dto.response.TaskResponse> openTasks = cmTaskRepository
				.findByParentIdAndStatus(parent.getId(), CmTaskStatus.OPEN).stream()
				.map(t -> toTaskResponse(t, now))
				.toList();

		List<AssignmentResponse> cmHistory = cmAssignmentRepository.findByParentIdOrderByAssignedAtDesc(parent.getId()).stream()
				.map(this::toAssignmentResponse)
				.toList();

		accessLogRecorder.record(actor, AccessLogRecordType.PARENT, parent.getId(), AccessLogAction.VIEW, null);

		return new ParentDetailResponse(
				parent.getPublicId(),
				displayName(parent),
				parent.getUser().getPhone(),
				parent.getUser().getEmail(),
				parent.getLastSelectedAddress() == null ? null : parent.getLastSelectedAddress().getAddressLine1(),
				null,
				stage,
				trialDay,
				parent.getUser().getCreatedAt(),
				null,
				parent.getCareManager() == null ? null : CmPersonRefMapper.of(parent.getCareManager()),
				isMyFamily,
				canEdit,
				children,
				currentNanny,
				notes,
				openComplaints,
				openTasks,
				cmHistory,
				note == null ? "1" : String.valueOf(note.getVersion()));
	}

	@Override
	@Transactional(readOnly = true)
	public BookingPageResponse listBookings(String parentPublicId, String cursor, Integer limit) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		int pageSize = clamp(limit);
		List<Booking> rows = bookingRepository.findByParentIdOrderByStartTimeDesc(parent.getId(), PageRequest.of(0, pageSize + 1));
		boolean hasMore = rows.size() > pageSize;
		List<Booking> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore ? CursorCodec.encode("page", page.get(page.size() - 1).getId()) : null;
		return new BookingPageResponse(page.stream().map(cmBookingMapper::map).toList(), nextCursor);
	}

	@Override
	@Transactional
	public PaymentSummaryResponse getPayments(User actor, String parentPublicId) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));

		List<PaymentTransaction> recent = paymentTransactionRepository.findByBookingParentId(parent.getId(), PageRequest.of(0, 10))
				.getContent();
		PaymentTransaction lastSuccess = recent.stream().filter(t -> t.getStatus() == PaymentStatus.SUCCESS).findFirst().orElse(null);

		ParentStage stage = parentStageService.resolveStage(parent);
		boolean trialRefundIssued = complaintRepository.findByParentIdOrderByRaisedAtDesc(parent.getId()).stream()
				.anyMatch(com.ektrepha.model.Complaint::isTrialRefundIssued);

		accessLogRecorder.record(actor, AccessLogRecordType.PARENT, parent.getId(), AccessLogAction.VIEW, "payments");

		return new PaymentSummaryResponse(
				stage == ParentStage.TRIAL ? "FREE_TRIAL" : "REGULAR",
				lastSuccess == null ? null : lastSuccess.getPlatformFeePaise(),
				lastSuccess == null ? null : lastSuccess.getCreatedAt(),
				trialRefundIssued,
				"INR");
	}

	@Override
	@Transactional(readOnly = true)
	public FamilyNotesResponse getNotes(String parentPublicId) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		return toFamilyNotes(parentNoteRepository.findById(parent.getId()).orElse(null));
	}

	@Override
	@Transactional
	public FamilyNotesResponse putNotes(CmCaller caller, User actor, String parentPublicId, String ifMatchVersion, FamilyNotesRequest request) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		Long ownerCmId = parent.getCareManager() == null ? null : parent.getCareManager().getId();
		careManagerAccessService.requireWrite(caller, ownerCmId, "this family's notes");

		ParentNote note = parentNoteRepository.findById(parent.getId()).orElse(null);
		int currentVersion = note == null ? 0 : note.getVersion();
		if (!String.valueOf(currentVersion == 0 ? 1 : currentVersion).equals(ifMatchVersion)) {
			throw new VersionMismatchException("If-Match does not match the current version; reload and retry");
		}

		if (note == null) {
			note = ParentNote.builder().parentId(parent.getId()).version(1).build();
		} else {
			note.setVersion(note.getVersion() + 1);
		}
		note.setChildRoutine(request.childRoutine());
		note.setAllergies(request.allergies());
		note.setPreferences(request.preferences());
		note.setExpectations(request.expectations());
		if (!caller.isAdmin()) {
			// updatedBy is a Care Manager reference — Admin writes leave it as the last CM who touched it.
			note.setUpdatedBy(com.ektrepha.model.CareManager.builder().id(caller.careManagerId()).build());
		}
		parentNoteRepository.save(note);

		accessLogRecorder.record(actor, AccessLogRecordType.PARENT, parent.getId(), AccessLogAction.EDIT, "notes");
		return toFamilyNotes(note);
	}

	@Override
	@Transactional(readOnly = true)
	public ContactPageResponse listContacts(String parentPublicId, String cursor, Integer limit) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Instant beforeContactedAt = decoded == null ? null : Instant.parse(decoded.sortKey());
		Long beforeId = decoded == null ? null : decoded.id();

		List<CmContact> rows = cmContactRepository.findPageForParent(parent.getId(), beforeContactedAt, beforeId,
				PageRequest.of(0, pageSize + 1));
		boolean hasMore = rows.size() > pageSize;
		List<CmContact> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore
				? CursorCodec.encode(page.get(page.size() - 1).getContactedAt().toString(), page.get(page.size() - 1).getId())
				: null;
		return new ContactPageResponse(page.stream().map(this::toContactResponse).toList(), nextCursor);
	}

	@Override
	@Transactional
	public ContactResponse logContact(CmCaller caller, String parentPublicId, CmLogContactRequest request) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		Long ownerCmId = parent.getCareManager() == null ? null : parent.getCareManager().getId();
		careManagerAccessService.requireWrite(caller, ownerCmId, "this family's contacts");

		CmTask task = request.taskId() == null ? null : cmTaskRepository.findByPublicId(request.taskId()).orElse(null);
		CmContact contact = CmContact.builder()
				.parent(parent)
				.careManager(com.ektrepha.model.CareManager.builder().id(caller.careManagerId()).build())
				.channel(request.channel())
				.direction(request.direction())
				.source(com.ektrepha.model.CmContactSource.CM_LOG)
				.summary(request.summary())
				.task(task)
				.loggedBy(com.ektrepha.model.CareManager.builder().id(caller.careManagerId()).build())
				.build();
		cmContactRepository.save(contact);
		return toContactResponse(contact);
	}

	private ParentListItemResponse toListItem(Parent parent, ParentStage stage, Integer trialDay, Instant lastContactAt,
			List<String> issues, Booking latestBooking) {
		return new ParentListItemResponse(
				parent.getPublicId(),
				displayName(parent),
				CmPhoneMasker.mask(parent.getUser().getPhone()),
				null,
				stage,
				trialDay,
				latestBooking == null || latestBooking.getNanny() == null ? null : CmPersonRefMapper.of(latestBooking.getNanny()),
				parent.getCareManager() == null ? null : CmPersonRefMapper.of(parent.getCareManager()),
				lastContactAt,
				issues);
	}

	private FamilyNotesResponse toFamilyNotes(ParentNote note) {
		if (note == null) {
			return new FamilyNotesResponse(null, null, null, null, null, null, "1");
		}
		return new FamilyNotesResponse(
				note.getChildRoutine(), note.getAllergies(), note.getPreferences(), note.getExpectations(),
				note.getUpdatedBy() == null ? null : CmPersonRefMapper.of(note.getUpdatedBy()),
				note.getUpdatedAt(), String.valueOf(note.getVersion()));
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

	private com.ektrepha.caremanager.dto.response.TaskResponse toTaskResponse(CmTask t, Instant now) {
		boolean overdue = t.getStatus() == CmTaskStatus.OPEN && t.getDueAt().isBefore(now);
		return new com.ektrepha.caremanager.dto.response.TaskResponse(
				t.getPublicId(), t.getType(), t.getPriority(), t.getStatus(), t.getDueAt(), overdue, t.getTitle(), t.getDetail(),
				CmPersonRefMapper.of(t.getParent()), t.getCareManager() == null ? null : CmPersonRefMapper.of(t.getCareManager()),
				t.getBooking() == null ? null : t.getBooking().getPublicId(),
				t.getComplaint() == null ? null : t.getComplaint().getPublicId(),
				t.getNote(), t.getCreatedAt(), t.getDoneAt());
	}

	private AssignmentResponse toAssignmentResponse(com.ektrepha.model.CmAssignment a) {
		return new AssignmentResponse(CmPersonRefMapper.of(a.getCareManager()), a.getAssignedAt(), a.getUnassignedAt(),
				a.getAssignedBy() == null ? null : CmPersonRefMapper.of(a.getAssignedBy()), a.getReason());
	}

	private ContactResponse toContactResponse(CmContact c) {
		return new ContactResponse(c.getPublicId(), c.getChannel(), c.getDirection(), c.getSource(), c.getSummary(),
				c.getTask() == null ? null : c.getTask().getPublicId(),
				c.getLoggedBy() == null ? null : CmPersonRefMapper.of(c.getLoggedBy()), c.getContactedAt());
	}

	private String displayName(Parent parent) {
		String first = parent.getFirstName() == null ? "" : parent.getFirstName();
		String last = parent.getLastName() == null ? "" : parent.getLastName();
		return (first + " " + last).trim();
	}

	private Double ageYears(LocalDate dob) {
		return dob == null ? null : (double) Period.between(dob, LocalDate.now(clock)).getYears();
	}

	private int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

}

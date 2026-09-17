package com.ektrepha.child.impl;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.child.AgeDisplay;
import com.ektrepha.child.dto.request.CareNotesUpdateRequest;
import com.ektrepha.child.dto.request.ChildUpsertRequest;
import com.ektrepha.child.dto.response.CareNotes;
import com.ektrepha.child.dto.response.ChildDetailResponse;
import com.ektrepha.child.dto.response.ChildSummaryResponse;
import com.ektrepha.child.dto.response.GuardianResponse;
import com.ektrepha.child.service.ChildService;
import com.ektrepha.exception.ChildInUseException;
import com.ektrepha.exception.ForbiddenChildAccessException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;
import com.ektrepha.model.ParentChildRelationship;
import com.ektrepha.parent.impl.ParentResolver;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ChildrenRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildServiceImpl implements ChildService {

	// A booking in any of these statuses still needs the child attached to it.
	private static final Set<BookingStatus> NON_TERMINAL = EnumSet.of(
			BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final ParentRepository parentRepository;
	private final ParentResolver parentResolver;
	private final ChildrenRepository childrenRepository;
	private final ParentChildRepository parentChildRepository;
	private final BookingRepository bookingRepository;
	// No com.fasterxml.jackson.databind.ObjectMapper bean is registered in this app (Spring Boot's
	// Jackson auto-configuration here wires the newer tools.jackson.* stack instead) — a plain
	// local instance is enough for this internal JSONB blob, which has no date/naming-strategy needs.
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	@Transactional(readOnly = true)
	public List<ChildSummaryResponse> list(Long userId) {
		// A user who hasn't completed P1/P2 yet has no parent row — C1 must render as an empty
		// state (PRD v2 §4), not an error, so this deliberately doesn't call resolveParent().
		Optional<Parent> parent = parentRepository.findByUserId(userId);
		if (parent.isEmpty()) {
			return List.of();
		}
		List<ParentChild> links = parentChildRepository.findByIdParentId(parent.get().getId());
		List<Long> childIds = links.stream().map(link -> link.getChild().getId()).toList();
		Map<Long, java.time.Instant> lastCareByChildId = lastCareByChildId(childIds);

		return links.stream()
				.map(link -> toSummary(link.getChild(), lastCareByChildId.get(link.getChild().getId())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ChildDetailResponse get(Long userId, Long childId) {
		Parent parent = resolveParent(userId);
		ParentChild link = resolveOwnedLink(parent.getId(), childId);
		return toDetail(link);
	}

	@Override
	@Transactional
	public ChildDetailResponse create(Long userId, ChildUpsertRequest request) {
		// A first-time user can add a child before ever completing P1/P2 (progressive profile
		// completion, PRD v2 §16.3) — auto-vivify the parent row rather than requiring it up front.
		Parent parent = parentResolver.resolveOrCreate(userId);
		boolean isFirstChild = parentChildRepository.findByIdParentId(parent.getId()).isEmpty();

		Children child = childrenRepository.save(Children.builder()
				.firstName(request.firstName())
				.lastName(request.lastName())
				.dob(request.dob())
				.gender(request.gender())
				.allergies(List.of())
				.build());

		ParentChild link = parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId()))
				.parent(parent)
				.child(child)
				.relationship(ParentChildRelationship.PARENT)
				.primaryContact(isFirstChild)
				.build());

		return toDetail(link);
	}

	@Override
	@Transactional
	public ChildDetailResponse update(Long userId, Long childId, ChildUpsertRequest request) {
		Parent parent = resolveParent(userId);
		ParentChild link = resolveOwnedLink(parent.getId(), childId);

		Children child = link.getChild();
		child.setFirstName(request.firstName());
		child.setLastName(request.lastName());
		child.setDob(request.dob());
		child.setGender(request.gender());
		childrenRepository.save(child);

		return toDetail(link);
	}

	@Override
	@Transactional
	public ChildDetailResponse updateCareNotes(Long userId, Long childId, CareNotesUpdateRequest request) {
		Parent parent = resolveParent(userId);
		ParentChild link = resolveOwnedLink(parent.getId(), childId);

		Children child = link.getChild();
		// allergies has a physical-safety consequence on a silent failure (PRD v2 §16.7) — a caller
		// that omits it (e.g. updating only medicalNotes) must never accidentally wipe it. Only an
		// explicit (possibly empty) list touches the column; null leaves the existing value alone.
		if (request.allergies() != null) {
			child.setAllergies(request.allergies());
		}
		child.setMetaData(writeMetaData(request));
		childrenRepository.save(child);

		return toDetail(link);
	}

	@Override
	@Transactional(readOnly = true)
	public List<GuardianResponse> guardians(Long userId, Long childId) {
		Parent parent = resolveParent(userId);
		resolveOwnedLink(parent.getId(), childId);

		return parentChildRepository.findByIdChildId(childId).stream()
				.map(this::toGuardian)
				.toList();
	}

	// Unlinks this parent from the child — the children row itself survives (another guardian may
	// still be linked, and past bookings must keep rendering even after every guardian unlinks).
	@Override
	@Transactional
	public void remove(Long userId, Long childId) {
		Parent parent = resolveParent(userId);
		ParentChild link = resolveOwnedLink(parent.getId(), childId);

		if (bookingRepository.existsByChildIdAndStatusIn(childId, NON_TERMINAL)) {
			throw new ChildInUseException("This child has an active booking — cancel or complete it first");
		}
		parentChildRepository.delete(link);
	}

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	// A parent may only act on a child they have a parent_child row for — 403, not 404, so a
	// non-owner can't tell a missing child apart from one they simply don't have access to.
	private ParentChild resolveOwnedLink(Long parentId, Long childId) {
		return parentChildRepository.findByIdParentIdAndIdChildId(parentId, childId)
				.orElseThrow(() -> new ForbiddenChildAccessException("childId does not belong to the requesting parent"));
	}

	private Map<Long, java.time.Instant> lastCareByChildId(List<Long> childIds) {
		if (childIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, java.time.Instant> result = new HashMap<>();
		for (Object[] row : bookingRepository.findLastCompletedByChildIds(childIds)) {
			result.put((Long) row[0], (java.time.Instant) row[1]);
		}
		return result;
	}

	private ChildSummaryResponse toSummary(Children child, java.time.Instant lastCareAt) {
		return new ChildSummaryResponse(
				child.getId(),
				child.getFirstName(),
				child.getLastName(),
				child.getProfilePhotoS3Key(),
				child.getDob(),
				AgeDisplay.of(child.getDob(), LocalDate.now()),
				child.getAllergies(),
				lastCareAt);
	}

	private ChildDetailResponse toDetail(ParentChild link) {
		Children child = link.getChild();
		int guardianCount = (int) parentChildRepository.countByIdChildId(child.getId());
		return new ChildDetailResponse(
				child.getId(),
				child.getFirstName(),
				child.getLastName(),
				child.getProfilePhotoS3Key(),
				child.getDob(),
				AgeDisplay.of(child.getDob(), LocalDate.now()),
				child.getGender(),
				readCareNotes(child),
				guardianCount,
				link.getRelationship().name(),
				link.isPrimaryContact());
	}

	private GuardianResponse toGuardian(ParentChild link) {
		Parent parent = link.getParent();
		return new GuardianResponse(
				parent.getId(),
				parent.getFirstName(),
				parent.getLastName(),
				link.getRelationship().name(),
				link.isPrimaryContact(),
				parent.getUser().getEmail(),
				parent.getUser().getPhone());
	}

	/** The non-allergy care-notes fields, as stored in {@code children.meta_data} JSONB. */
	private record CareNotesMeta(String medicalNotes, String routine, String comfort, String doNot) {
	}

	private CareNotes readCareNotes(Children child) {
		CareNotesMeta meta = parseMetaData(child.getMetaData());
		return new CareNotes(child.getAllergies(), meta.medicalNotes(), meta.routine(), meta.comfort(), meta.doNot());
	}

	private String writeMetaData(CareNotesUpdateRequest request) {
		try {
			return objectMapper.writeValueAsString(
					new CareNotesMeta(request.medicalNotes(), request.routine(), request.comfort(), request.doNot()));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize care notes", e);
		}
	}

	private CareNotesMeta parseMetaData(String json) {
		if (json == null || json.isBlank()) {
			return new CareNotesMeta(null, null, null, null);
		}
		try {
			return objectMapper.readValue(json, CareNotesMeta.class);
		} catch (Exception e) {
			log.warn("Unreadable children.meta_data, treating as empty care notes", e);
			return new CareNotesMeta(null, null, null, null);
		}
	}

}

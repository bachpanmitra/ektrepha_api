package com.ektrepha.elder.impl;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ektrepha.elder.dto.ElderCarePreferences;
import com.ektrepha.elder.dto.request.ElderUpsertRequest;
import com.ektrepha.elder.dto.response.ElderDetailResponse;
import com.ektrepha.elder.dto.response.ElderSummaryResponse;
import com.ektrepha.elder.service.ElderService;
import com.ektrepha.exception.ElderInUseException;
import com.ektrepha.exception.ForbiddenElderAccessException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Elder;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentElder;
import com.ektrepha.model.ParentElderId;
import com.ektrepha.parent.impl.ParentResolver;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ElderRepository;
import com.ektrepha.repository.ParentElderRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.storage.S3PhotoUrlService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Elder Care's profile CRUD — mirrors {@code ChildServiceImpl}'s shape via the {@code parent_elder} join table instead of {@code parent_child}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElderServiceImpl implements ElderService {

	// A booking in any of these statuses still needs the elder attached to it — mirrors ChildServiceImpl#NON_TERMINAL.
	private static final Set<BookingStatus> NON_TERMINAL = EnumSet.of(
			BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final ParentRepository parentRepository;
	private final ParentResolver parentResolver;
	private final ElderRepository elderRepository;
	private final ParentElderRepository parentElderRepository;
	private final BookingRepository bookingRepository;
	private final S3PhotoUrlService s3PhotoUrlService;
	// No com.fasterxml.jackson.databind.ObjectMapper bean is registered in this app (see
	// ChildServiceImpl's identical field) — a plain local instance is enough for this internal
	// JSONB blob, which has no date/naming-strategy needs.
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	@Transactional(readOnly = true)
	public List<ElderSummaryResponse> list(Long userId) {
		// No parent row yet - render an empty state, not an error, same as ChildServiceImpl#list.
		Optional<Parent> parent = parentRepository.findByUserId(userId);
		if (parent.isEmpty()) {
			return List.of();
		}
		return parentElderRepository.findByIdParentId(parent.get().getId()).stream()
				.filter(link -> link.getElder().getRemovedAt() == null)
				.map(this::toSummary)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ElderDetailResponse get(Long userId, Long elderId) {
		Parent parent = resolveParent(userId);
		ParentElder link = resolveOwnedLink(parent.getId(), elderId);
		return toDetail(link);
	}

	@Override
	@Transactional
	public ElderDetailResponse create(Long userId, ElderUpsertRequest request) {
		// Auto-vivify the parent row, same as ChildServiceImpl#create - an elder can be added before
		// the parent ever completes P1/P2.
		Parent parent = parentResolver.resolveOrCreate(userId);
		boolean isFirstElder = parentElderRepository.findByIdParentId(parent.getId()).isEmpty();

		Elder elder = elderRepository.save(Elder.builder()
				.firstName(request.firstName())
				.lastName(request.lastName())
				.age(request.age())
				.gender(request.gender())
				.mobility(request.mobility())
				.conditions(request.conditions() == null ? List.of() : request.conditions())
				.allergies(request.allergies() == null ? List.of() : request.allergies())
				.medicationNotes(request.medicationNotes())
				.preferredLanguage(request.preferredLanguage())
				.emergencyContactName(request.emergencyContactName())
				.emergencyContactPhone(request.emergencyContactPhone())
				.doctorName(request.doctorName())
				.doctorPhone(request.doctorPhone())
				.metaData(writeCarePreferences(request.carePreferences()))
				.build());

		ParentElder link = parentElderRepository.save(ParentElder.builder()
				.id(new ParentElderId(parent.getId(), elder.getId()))
				.parent(parent).elder(elder)
				.relationship(request.relationship())
				.primaryContact(isFirstElder)
				.build());

		return toDetail(link);
	}

	@Override
	@Transactional
	public ElderDetailResponse update(Long userId, Long elderId, ElderUpsertRequest request) {
		Parent parent = resolveParent(userId);
		ParentElder link = resolveOwnedLink(parent.getId(), elderId);

		Elder elder = link.getElder();
		elder.setFirstName(request.firstName());
		elder.setLastName(request.lastName());
		elder.setAge(request.age());
		elder.setGender(request.gender());
		elder.setMobility(request.mobility());
		elder.setConditions(request.conditions() == null ? List.of() : request.conditions());
		elder.setAllergies(request.allergies() == null ? List.of() : request.allergies());
		elder.setMedicationNotes(request.medicationNotes());
		elder.setPreferredLanguage(request.preferredLanguage());
		elder.setEmergencyContactName(request.emergencyContactName());
		elder.setEmergencyContactPhone(request.emergencyContactPhone());
		elder.setDoctorName(request.doctorName());
		elder.setDoctorPhone(request.doctorPhone());
		elder.setMetaData(writeCarePreferences(request.carePreferences()));
		elderRepository.save(elder);

		link.setRelationship(request.relationship());
		parentElderRepository.save(link);

		return toDetail(link);
	}

	// Soft delete (set removed_at) rather than unlinking like ChildServiceImpl#remove - the
	// parent_elder link survives, but the elder stops showing up in list()/booking flows.
	@Override
	@Transactional
	public void remove(Long userId, Long elderId) {
		Parent parent = resolveParent(userId);
		ParentElder link = resolveOwnedLink(parent.getId(), elderId);

		if (bookingRepository.existsByElderIdAndStatusIn(elderId, NON_TERMINAL)) {
			throw new ElderInUseException("This elder has an active booking — cancel or complete it first");
		}
		Elder elder = link.getElder();
		elder.setRemovedAt(Instant.now());
		elderRepository.save(elder);
	}

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	// A parent may only act on an elder they have a parent_elder row for — 403, not 404, mirroring
	// ChildServiceImpl#resolveOwnedLink.
	private ParentElder resolveOwnedLink(Long parentId, Long elderId) {
		return parentElderRepository.findByIdParentIdAndIdElderId(parentId, elderId)
				.orElseThrow(() -> new ForbiddenElderAccessException("elderId does not belong to the requesting parent"));
	}

	private ElderSummaryResponse toSummary(ParentElder link) {
		Elder elder = link.getElder();
		return new ElderSummaryResponse(
				elder.getId(),
				elder.getFirstName(),
				elder.getLastName(),
				s3PhotoUrlService.presign(elder.getProfilePhotoS3Key()),
				elder.getAge(),
				elder.getGender(),
				link.getRelationship() == null ? null : link.getRelationship().name(),
				elder.getMobility().name(),
				elder.getConditions());
	}

	private ElderDetailResponse toDetail(ParentElder link) {
		Elder elder = link.getElder();
		return new ElderDetailResponse(
				elder.getId(),
				elder.getFirstName(),
				elder.getLastName(),
				s3PhotoUrlService.presign(elder.getProfilePhotoS3Key()),
				elder.getAge(),
				elder.getGender(),
				link.getRelationship() == null ? null : link.getRelationship().name(),
				elder.getMobility().name(),
				elder.getConditions(),
				elder.getAllergies(),
				elder.getMedicationNotes(),
				elder.getPreferredLanguage(),
				elder.getEmergencyContactName(),
				elder.getEmergencyContactPhone(),
				elder.getDoctorName(),
				elder.getDoctorPhone(),
				readCarePreferences(elder.getMetaData()));
	}

	// elders.meta_data stores ElderCarePreferences (M7/M8) as a plain JSON object - there's nothing
	// else in it today, so no merge-with-existing-keys logic is needed; a save always writes the
	// caller's whole ElderCarePreferences (or null to clear it) as the entire column.
	private String writeCarePreferences(ElderCarePreferences prefs) {
		if (prefs == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(prefs);
		} catch (JsonProcessingException e) {
			log.error("Could not serialize ElderCarePreferences", e);
			return null;
		}
	}

	// Null/blank/unparseable all read back as "not filled in yet" rather than a 500 - this is
	// optional profile depth, never something a booking flow depends on.
	private ElderCarePreferences readCarePreferences(String metaData) {
		if (metaData == null || metaData.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(metaData, ElderCarePreferences.class);
		} catch (JsonProcessingException e) {
			log.warn("Could not parse elder.meta_data as ElderCarePreferences", e);
			return null;
		}
	}

}

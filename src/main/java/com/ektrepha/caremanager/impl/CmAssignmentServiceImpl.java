package com.ektrepha.caremanager.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.event.ParentAssignedToCareManagerEvent;
import com.ektrepha.caremanager.event.ParentSignedUpPayload;
import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmArea;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmAssignmentReason;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CmAssignmentServiceImpl implements CmAssignmentService {

	private final CareManagerRepository careManagerRepository;
	private final CmAssignmentRepository cmAssignmentRepository;
	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final CmAreaResolver cmAreaResolver;
	private final OutboxEventWriter outboxEventWriter;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	@Transactional
	public void autoAssignParentIfNeeded(Parent parent) {
		if (parent.getCareManager() != null) {
			return;
		}
		Optional<CmArea> area = cmAreaResolver.resolve(parent);
		if (area.isEmpty()) {
			log.info("Skipping CM auto-assignment for parent {} — area not resolvable yet", parent.getId());
			return;
		}
		Optional<Pick> pick = pickForAutoAssignment(area.get());
		if (pick.isEmpty()) {
			log.warn("No active Care Manager in area {} — parent {} left unassigned", area.get(), parent.getId());
			return;
		}

		assignParentTo(parent, pick.get().careManager(), CmAssignmentReason.SIGNUP, null);
		outboxEventWriter.write("ParentSignedUp", new ParentSignedUpPayload(parent.getId()));
		eventPublisher.publishEvent(new ParentAssignedToCareManagerEvent(parent.getId(), pick.get().careManager().getId(), pick.get().overCap()));
	}

	@Override
	@Transactional
	public CmAssignment assignParentTo(Parent parent, CareManager careManager, CmAssignmentReason reason, User assignedBy) {
		cmAssignmentRepository.findByParentIdAndUnassignedAtIsNull(parent.getId()).ifPresent(open -> {
			open.setUnassignedAt(Instant.now());
			cmAssignmentRepository.save(open);
		});
		CmAssignment created = cmAssignmentRepository.save(CmAssignment.builder()
				.parent(parent)
				.careManager(careManager)
				.reason(reason)
				.assignedBy(assignedBy)
				.build());
		parent.setCareManager(careManager);
		parentRepository.save(parent);
		return created;
	}

	@Override
	@Transactional
	public CmAssignment assignNannyTo(Nanny nanny, CareManager careManager, CmAssignmentReason reason, User assignedBy) {
		cmAssignmentRepository.findByNannyIdAndUnassignedAtIsNull(nanny.getId()).ifPresent(open -> {
			open.setUnassignedAt(Instant.now());
			cmAssignmentRepository.save(open);
		});
		CmAssignment created = cmAssignmentRepository.save(CmAssignment.builder()
				.nanny(nanny)
				.careManager(careManager)
				.reason(reason)
				.assignedBy(assignedBy)
				.build());
		nanny.setCareManager(careManager);
		nannyRepository.save(nanny);
		return created;
	}

	@Override
	public long countActiveParents(CareManager careManager) {
		return cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(careManager.getId());
	}

	@Override
	public long countActiveNannies(CareManager careManager) {
		return cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndNannyIsNotNull(careManager.getId());
	}

	/**
	 * Selection order: prefer CMs with {@code autoAssignNewSignups=true}; within that pool (or the
	 * full active pool if the preferred one has no one with room), pick fewest active parents under
	 * {@code max_parents}; if literally nobody in the area has room, fall back to least loaded
	 * (preferring the opt-in pool) and flag {@code overCap} so the caller alerts the senior manager.
	 */
	private Optional<Pick> pickForAutoAssignment(CmArea area) {
		List<CareManager> active = careManagerRepository.findByAreaAndActiveTrue(area);
		if (active.isEmpty()) {
			return Optional.empty();
		}
		List<CareManager> preferred = active.stream().filter(CareManager::isAutoAssignNewSignups).toList();
		List<CareManager> preferredPool = preferred.isEmpty() ? active : preferred;

		Optional<Pick> withinPreferredCap = fewestUnderCap(preferredPool, false);
		if (withinPreferredCap.isPresent()) {
			return withinPreferredCap;
		}
		Optional<Pick> withinFullCap = fewestUnderCap(active, false);
		if (withinFullCap.isPresent()) {
			return withinFullCap;
		}
		// Nobody in the area has room — least loaded anyway, preferring the opt-in pool.
		return fewestUnderCap(preferredPool, true);
	}

	private Optional<Pick> fewestUnderCap(List<CareManager> pool, boolean ignoreCap) {
		return pool.stream()
				.map(cm -> new Pick(cm, countActiveParents(cm), ignoreCap))
				.filter(pick -> ignoreCap || pick.activeParents() < pick.careManager().getMaxParents())
				.min(Comparator.comparingLong(Pick::activeParents).thenComparing(p -> p.careManager().getId()))
				.map(p -> new Pick(p.careManager(), p.activeParents(), ignoreCap));
	}

	private record Pick(CareManager careManager, long activeParents, boolean overCap) {
	}

}

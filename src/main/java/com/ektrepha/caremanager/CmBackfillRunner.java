package com.ektrepha.caremanager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmArea;
import com.ektrepha.model.CmAssignmentReason;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.BookingRepository.NannyAreaBookingCount;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-off ops tooling: assigns every parent/nanny that predates this feature (care_manager_id
 * still NULL) a Care Manager, reason BACKFILL. Off by default ({@code app.care-manager.backfill.
 * enabled}); dry-run by default ({@code app.care-manager.backfill.dry-run}) — logs the plan
 * (counts per CM) without writing anything, so staging can verify it before a real run. The final
 * migration setting {@code care_manager_id NOT NULL} should only ship once this has been run for
 * real and verified.
 * <p>
 * Parents: fewest active parents among active CMs in their area (ignoring max_parents — this is a
 * one-time catch-up, not a new signup, so the cap doesn't apply the same way); skipped if their
 * area isn't resolvable. Nannies: the CM of the area she has the most past bookings in (via each
 * booking's address pincode); skipped if she has no bookings with a resolvable area at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmBackfillRunner implements CommandLineRunner {

	private final AppProperties appProperties;
	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final CareManagerRepository careManagerRepository;
	private final BookingRepository bookingRepository;
	private final CmAreaResolver cmAreaResolver;
	private final CmAssignmentService cmAssignmentService;

	@Override
	public void run(String... args) {
		if (!appProperties.careManager().backfill().enabled()) {
			return;
		}
		boolean dryRun = appProperties.careManager().backfill().dryRun();
		log.info("Starting Care Manager backfill ({})", dryRun ? "DRY RUN" : "APPLYING");
		backfillParents(dryRun);
		backfillNannies(dryRun);
		log.info("Care Manager backfill complete ({})", dryRun ? "DRY RUN" : "APPLIED");
	}

	private void backfillParents(boolean dryRun) {
		List<Parent> unassigned = parentRepository.findByCareManagerIsNull();
		Map<Long, Integer> plannedPerCm = new HashMap<>();
		int skippedNoArea = 0;

		for (Parent parent : unassigned) {
			Optional<CmArea> area = cmAreaResolver.resolve(parent);
			if (area.isEmpty()) {
				skippedNoArea++;
				continue;
			}
			Optional<CareManager> pick = leastLoadedActiveInArea(area.get());
			if (pick.isEmpty()) {
				continue;
			}
			plannedPerCm.merge(pick.get().getId(), 1, Integer::sum);
			if (!dryRun) {
				cmAssignmentService.assignParentTo(parent, pick.get(), CmAssignmentReason.BACKFILL, null);
			}
		}
		log.info("Parent backfill: {} unassigned, {} skipped (no resolvable area), planned per CM id: {}",
				unassigned.size(), skippedNoArea, plannedPerCm);
	}

	private void backfillNannies(boolean dryRun) {
		List<Nanny> unassigned = nannyRepository.findByCareManagerIsNull();
		if (unassigned.isEmpty()) {
			return;
		}

		Map<Long, CmArea> busiestAreaByNannyId = new HashMap<>();
		Map<Long, Long> bestCountByNannyId = new HashMap<>();
		for (NannyAreaBookingCount row : bookingRepository.findNannyAreaBookingCounts()) {
			Long best = bestCountByNannyId.get(row.getNannyId());
			if (best == null || row.getBookingCount() > best) {
				bestCountByNannyId.put(row.getNannyId(), row.getBookingCount());
				busiestAreaByNannyId.put(row.getNannyId(), CmArea.valueOf(row.getArea()));
			}
		}

		Map<Long, Integer> plannedPerCm = new HashMap<>();
		int skippedNoBookings = 0;
		for (Nanny nanny : unassigned) {
			CmArea area = busiestAreaByNannyId.get(nanny.getId());
			if (area == null) {
				skippedNoBookings++;
				continue;
			}
			Optional<CareManager> pick = leastLoadedActiveInArea(area);
			if (pick.isEmpty()) {
				continue;
			}
			plannedPerCm.merge(pick.get().getId(), 1, Integer::sum);
			if (!dryRun) {
				cmAssignmentService.assignNannyTo(nanny, pick.get(), CmAssignmentReason.BACKFILL, null);
			}
		}
		log.info("Nanny backfill: {} unassigned, {} skipped (no bookings with a resolvable area), planned per CM id: {}",
				unassigned.size(), skippedNoBookings, plannedPerCm);
	}

	private Optional<CareManager> leastLoadedActiveInArea(CmArea area) {
		return careManagerRepository.findByAreaAndActiveTrue(area).stream()
				.min(Comparator.comparingLong(cmAssignmentService::countActiveParents).thenComparing(CareManager::getId));
	}

}

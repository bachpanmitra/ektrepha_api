package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ektrepha.caremanager.service.ParentStageService;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentTrialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParentStageServiceImpl implements ParentStageService {

	private static final Duration LAPSED_AFTER = Duration.ofDays(60);

	private final ParentTrialRepository parentTrialRepository;
	private final BookingRepository bookingRepository;
	private final Clock clock;

	@Override
	public ParentStage resolveStage(Parent parent) {
		if (activeTrial(parent).isPresent()) {
			return ParentStage.TRIAL;
		}
		if (bookingRepository.countByParentId(parent.getId()) == 0) {
			return ParentStage.NEW;
		}
		Optional<Booking> mostRecent = bookingRepository.findFirstByParentIdOrderByStartTimeDesc(parent.getId());
		if (mostRecent.isPresent() && mostRecent.get().getStartTime().isBefore(Instant.now(clock).minus(LAPSED_AFTER))) {
			return ParentStage.LAPSED;
		}
		return ParentStage.REGULAR;
	}

	@Override
	public Integer resolveTrialDay(Parent parent) {
		return activeTrial(parent)
				.map(trial -> {
					long hoursElapsed = Duration.between(trial.getStartedAt(), Instant.now(clock)).toHours();
					int day = (int) (hoursElapsed / 24) + 1;
					return Math.min(Math.max(day, 1), 5);
				})
				.orElse(null);
	}

	private Optional<ParentTrial> activeTrial(Parent parent) {
		return parentTrialRepository.findByParentId(parent.getId()).filter(t -> t.getStatus() == TrialStatus.ACTIVE);
	}

}

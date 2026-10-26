package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.LiveInArrangement;

public interface LiveInArrangementRepository extends JpaRepository<LiveInArrangement, Long> {

	// Every day row of a live-in stay carries the anchor's id as its recurrenceGroupId, so any one
	// of them finds the stay's terms through this.
	Optional<LiveInArrangement> findByAnchorBookingId(Long anchorBookingId);

}

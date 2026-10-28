package com.ektrepha.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.LiveInArrangement;

public interface LiveInArrangementRepository extends JpaRepository<LiveInArrangement, Long> {

	// Every day row of a live-in stay carries the anchor's id as its recurrenceGroupId, so any one
	// of them finds the stay's terms through this.
	Optional<LiveInArrangement> findByAnchorBookingId(Long anchorBookingId);


	// Ops team board - the terms of every live-in stay on the board, in one query.
	List<LiveInArrangement> findByAnchorBookingIdIn(Collection<Long> anchorBookingIds);

}

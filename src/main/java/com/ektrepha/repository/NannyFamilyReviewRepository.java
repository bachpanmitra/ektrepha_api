package com.ektrepha.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyFamilyReview;

public interface NannyFamilyReviewRepository extends JpaRepository<NannyFamilyReview, Long> {

	boolean existsByBookingId(Long bookingId);

	// Shift details' "family rating" - every nanny's private rating of this family, averaged.
	@Query("SELECT AVG(r.stars) FROM NannyFamilyReview r WHERE r.parent.id = :parentId")
	Double averageStarsForParent(@Param("parentId") Long parentId);

}

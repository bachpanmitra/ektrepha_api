package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.BannerCareType;
import com.ektrepha.model.HomeBanner;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, UUID> {

	List<HomeBanner> findByDeletedAtIsNullOrderBySortOrderAsc();

	@Query("""
			SELECT b FROM HomeBanner b
			WHERE b.deletedAt IS NULL AND b.active = true AND b.careType = :careType
			AND b.startAt <= :now AND (b.endAt IS NULL OR b.endAt > :now)
			ORDER BY b.sortOrder ASC
			""")
	List<HomeBanner> findLiveByCareType(@Param("careType") BannerCareType careType, @Param("now") Instant now);

	// Used when the caller has no care-type context to filter by - this app's home page doesn't
	// have the PRD's "Child care / Elder care switch" yet (see HomeBannerController), so Phase 1
	// shows every live banner regardless of care_type rather than defaulting to one arbitrarily.
	@Query("""
			SELECT b FROM HomeBanner b
			WHERE b.deletedAt IS NULL AND b.active = true
			AND b.startAt <= :now AND (b.endAt IS NULL OR b.endAt > :now)
			ORDER BY b.sortOrder ASC
			""")
	List<HomeBanner> findAllLive(@Param("now") Instant now);

}

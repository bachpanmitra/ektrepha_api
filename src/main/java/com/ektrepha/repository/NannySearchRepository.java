package com.ektrepha.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Hand-built dynamic SQL over {@link NamedParameterJdbcTemplate}, not a {@code JpaRepository} or
 * {@code @Query(nativeQuery=true)}. The filter set mixes always-present params (location, radius,
 * time window, the hardcoded verification filter) with optional scalar/multi-value params whose
 * presence varies per request — a single static native query string would bury the mandatory
 * verification filter among a pile of "(:param IS NULL OR ...)" conditionals. Building the SQL as
 * fragments in code instead lets the verification clause be a literal, unconditional first line
 * with no {@code if} around it, so no filter combination can ever remove it.
 */
@Repository
@RequiredArgsConstructor
public class NannySearchRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** Resolved, validated inputs the service layer hands to the search query. */
	public record Criteria(
			double lat,
			double lng,
			int radiusKm,
			Instant windowStart,
			Instant windowEnd,
			BigDecimal minPrice,
			BigDecimal maxPrice,
			Integer minYearsExperience,
			String educationLevel,
			List<Long> languageIds,
			List<Long> skillIds,
			int candidateFetchLimit) {
	}

	/**
	 * One raw candidate nanny row, pre-ranking. Ranking/scoring happens in the service layer, not
	 * in SQL. {@code distanceM} is meters, not kilometers — {@code earth_distance()} already
	 * returns meters (the earthdistance module's default earth radius is in meters), and the
	 * "Nanny Proximity Search" PRD specifies distance_m as the response unit; the client converts
	 * to "X km away" for display.
	 */
	public record CandidateRow(
			Long nannyId,
			String firstName,
			String lastName,
			BigDecimal hourlyRate,
			Integer yearsExperience,
			String educationLevel,
			String profilePhotoS3Key,
			double distanceM) {
	}

	// Runs the bounding-box + exact-distance geo filter (idx_nanny_service_area_geo), the
	// unconditional VERIFIED-only + non-deleted-account filters, every optional filter present
	// on the request, and the booking-overlap exclusion, in one round trip.
	public List<CandidateRow> findCandidates(Criteria criteria) {
		StringBuilder sql = new StringBuilder("""
				SELECT n.id AS nanny_id, n.first_name, n.last_name, n.hourly_rate,
				       n.years_experience, n.education_level, n.profile_photo_s3_key,
				       earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(nsa.lat, nsa.lng)) AS distance_m
				FROM nanny n
				JOIN users u ON u.id = n.user_id
				JOIN nanny_service_area nsa ON nsa.nanny_id = n.id
				WHERE n.overall_verification_status = 3
				  AND u.status = 0
				  AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters) @> ll_to_earth(nsa.lat, nsa.lng)
				  AND earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(nsa.lat, nsa.lng)) <= :radiusMeters
				  AND NOT EXISTS (
				      SELECT 1 FROM booking b
				      WHERE b.nanny_id = n.id
				        AND b.status IN (1, 2, 3)
				        AND tstzrange(b.start_time, b.end_time) && tstzrange(:windowStart, :windowEnd)
				  )
				""");

		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("lat", criteria.lat())
				.addValue("lng", criteria.lng())
				.addValue("radiusMeters", criteria.radiusKm() * 1000.0)
				.addValue("windowStart", Timestamp.from(criteria.windowStart()))
				.addValue("windowEnd", Timestamp.from(criteria.windowEnd()));

		if (criteria.minPrice() != null) {
			sql.append(" AND n.hourly_rate >= :minPrice");
			params.addValue("minPrice", criteria.minPrice());
		}
		if (criteria.maxPrice() != null) {
			sql.append(" AND n.hourly_rate <= :maxPrice");
			params.addValue("maxPrice", criteria.maxPrice());
		}
		if (criteria.minYearsExperience() != null) {
			sql.append(" AND n.years_experience >= :minYears");
			params.addValue("minYears", criteria.minYearsExperience());
		}
		if (criteria.educationLevel() != null) {
			sql.append(" AND n.education_level = :educationLevel");
			params.addValue("educationLevel", criteria.educationLevel());
		}
		// OR semantics: matches a nanny who speaks/has ANY of the requested languages/skills.
		if (criteria.languageIds() != null && !criteria.languageIds().isEmpty()) {
			sql.append(" AND EXISTS (SELECT 1 FROM nanny_language nl WHERE nl.nanny_id = n.id AND nl.language_id IN (:languageIds))");
			params.addValue("languageIds", criteria.languageIds());
		}
		if (criteria.skillIds() != null && !criteria.skillIds().isEmpty()) {
			sql.append(" AND EXISTS (SELECT 1 FROM nanny_skill ns WHERE ns.nanny_id = n.id AND ns.skill_id IN (:skillIds))");
			params.addValue("skillIds", criteria.skillIds());
		}

		sql.append(" ORDER BY distance_m ASC LIMIT :limit");
		params.addValue("limit", criteria.candidateFetchLimit());

		return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new CandidateRow(
				rs.getLong("nanny_id"),
				rs.getString("first_name"),
				rs.getString("last_name"),
				rs.getBigDecimal("hourly_rate"),
				(Integer) rs.getObject("years_experience"),
				rs.getString("education_level"),
				rs.getString("profile_photo_s3_key"),
				rs.getDouble("distance_m")));
	}

}

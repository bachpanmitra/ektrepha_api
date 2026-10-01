package com.ektrepha.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Backs the admin "Assign a nanny" candidate list: every nanny actively mapped to a booking's
 * zone+service via {@code caregiver_zone_mapping}, with distance to the booking's address when
 * available. Hand-built SQL over {@link NamedParameterJdbcTemplate}, same approach as
 * {@link NannySearchRepository}, since the {@code DISTINCT ON} dedup (a nanny can have more than
 * one {@code nanny_service_area} row) and the nullable distance calc aren't natural fits for JPQL.
 */
@Repository
@RequiredArgsConstructor
public class AdminBookingCandidateRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** {@code distanceM} is null when the nanny has no {@code nanny_service_area} row, or the booking's address has no lat/lng on file. */
	public record CandidateRow(Long nannyId, String firstName, String lastName, BigDecimal hourlyRate, Double distanceM) {
	}

	public List<CandidateRow> findMappedCandidates(Long zoneAreaId, Long serviceTypeId, Double bookingLat, Double bookingLng) {
		// lat/lng are given an explicit SQL type — a plain null bound as :lat has no inferable type in
		// "CASE WHEN :lat IS NULL ...", and Postgres throws "could not determine data type of
		// parameter" for it otherwise (same class of issue as BookingRepository#searchForAdmin's
		// comment on null Instant binds), hit whenever the booking's address has no lat/lng on file.
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("zoneAreaId", zoneAreaId)
				.addValue("serviceTypeId", serviceTypeId)
				.addValue("lat", bookingLat, Types.DOUBLE)
				.addValue("lng", bookingLng, Types.DOUBLE);

		String sql = """
				SELECT DISTINCT ON (n.id) n.id AS nanny_id, n.first_name, n.last_name, n.hourly_rate,
				       CASE WHEN :lat IS NULL OR nsa.lat IS NULL THEN NULL
				            ELSE earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(nsa.lat, nsa.lng)) END AS distance_m
				FROM nanny n
				JOIN caregiver_zone_mapping czm ON czm.nanny_id = n.id
				JOIN users u ON u.id = n.user_id
				LEFT JOIN nanny_service_area nsa ON nsa.nanny_id = n.id
				WHERE czm.zone_area_id = :zoneAreaId AND czm.service_type_id = :serviceTypeId AND czm.is_active = true
				  AND u.status = 0
				ORDER BY n.id, distance_m ASC NULLS LAST
				""";

		return jdbcTemplate.query(sql, params, (rs, rowNum) -> new CandidateRow(
				rs.getLong("nanny_id"),
				rs.getString("first_name"),
				rs.getString("last_name"),
				rs.getBigDecimal("hourly_rate"),
				(Double) rs.getObject("distance_m")));
	}

}

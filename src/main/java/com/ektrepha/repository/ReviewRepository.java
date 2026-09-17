package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByBookingId(Long bookingId);

	Optional<Review> findByBookingId(Long bookingId);

	// S1 "What parents say".
	Page<Review> findByNannyIdOrderByCreatedAtDesc(Long nannyId, Pageable pageable);

	// S1's "4.9 / 48 reviews" header — one row: [0] = AVG(rating) as Double (or null with zero
	// reviews), [1] = COUNT(*) as Long. Declared as List<Object[]>, not a bare Object[] — Spring
	// Data executes a no-GROUP-BY multi-select via getResultList(), and a bare Object[] return type
	// gets the single row wrapped in an extra array layer rather than unwrapped.
	@Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.nanny.id = :nannyId")
	List<Object[]> findRatingAggregate(@Param("nannyId") Long nannyId);

	// B1/H1 card list — one batched aggregate query for every nanny on the page, avoiding a
	// per-card rating lookup.
	@Query("SELECT r.nanny.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.nanny.id IN :nannyIds GROUP BY r.nanny.id")
	List<Object[]> findRatingAggregatesByNannyIds(@Param("nannyIds") List<Long> nannyIds);

}

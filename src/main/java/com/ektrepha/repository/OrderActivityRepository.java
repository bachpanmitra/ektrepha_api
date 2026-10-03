package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.OrderActivity;
import com.ektrepha.model.OrderActivityType;

public interface OrderActivityRepository extends JpaRepository<OrderActivity, Long> {

	List<OrderActivity> findByBookingIdOrderByOccurredAtAsc(Long bookingId);

	// join fetch is safe to paginate here - every hop (booking/parent/user/serviceType) is a
	// *-to-one association, so there's no collection-fetch row multiplication for Page to mis-count.
	@Query("select a from OrderActivity a "
			+ "join fetch a.booking b join fetch b.parent p join fetch p.user u join fetch b.serviceType st "
			+ "where (:eventType is null or a.eventType = :eventType) "
			+ "order by a.occurredAt desc")
	Page<OrderActivity> findFeed(@Param("eventType") OrderActivityType eventType, Pageable pageable);

}

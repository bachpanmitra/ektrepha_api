package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<Notification> findByIdAndUserId(Long id, Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
	void markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

}

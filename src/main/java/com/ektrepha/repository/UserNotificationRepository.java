package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	List<UserNotification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

	Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

	@Modifying
	@Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

}

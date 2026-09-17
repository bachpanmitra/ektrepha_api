package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NotificationCategory;
import com.ektrepha.model.UserNotificationPreference;

public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, Long> {

	List<UserNotificationPreference> findByUserId(Long userId);

	Optional<UserNotificationPreference> findByUserIdAndCategory(Long userId, NotificationCategory category);

}

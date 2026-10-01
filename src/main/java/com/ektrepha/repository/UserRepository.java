package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.User;
import com.ektrepha.model.UserType;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByPhone(String phone);

	Optional<User> findByGoogleId(String googleId);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	List<User> findByUserTypeOrderByCreatedAtDesc(UserType userType);

}

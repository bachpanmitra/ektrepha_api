package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.User;
import com.ektrepha.model.WaitlistSignup;

public interface WaitlistSignupRepository extends JpaRepository<WaitlistSignup, Long> {

	Optional<WaitlistSignup> findByUser(User user);

}

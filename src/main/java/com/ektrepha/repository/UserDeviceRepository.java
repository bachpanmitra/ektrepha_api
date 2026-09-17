package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.UserDevice;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

	Optional<UserDevice> findByPushToken(String pushToken);

}

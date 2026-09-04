package com.ektrepha.auth.security;

import java.time.Duration;

import com.ektrepha.model.User;

import io.jsonwebtoken.Claims;

/** Issues and validates access tokens. See {@link JwtServiceImpl}. */
public interface JwtService {

	String generateAccessToken(User user);

	Claims parseAndValidate(String token);

	Duration getAccessTokenTtl();

}

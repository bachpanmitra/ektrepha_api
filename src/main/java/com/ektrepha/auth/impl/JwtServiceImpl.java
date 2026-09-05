package com.ektrepha.auth.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.ektrepha.model.User;
import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.auth.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtServiceImpl implements JwtService {

	private final SecretKey signingKey;
	private final Duration accessTokenTtl;

	public JwtServiceImpl(AppProperties appProperties) {
		this.signingKey = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = Duration.ofMinutes(appProperties.jwt().accessTokenTtlMinutes());
	}

	@Override
	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		var builder = Jwts.builder()
				.subject(user.getId().toString())
				.claim(SecurityConstants.CLAIM_USER_TYPE, user.getUserType().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)));

		if (user.getEmail() != null) {
			builder.claim(SecurityConstants.CLAIM_EMAIL, user.getEmail());
		}
		if (user.getPhone() != null) {
			builder.claim(SecurityConstants.CLAIM_PHONE, user.getPhone());
		}

		return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
	}

	@Override
	public Claims parseAndValidate(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	@Override
	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

}

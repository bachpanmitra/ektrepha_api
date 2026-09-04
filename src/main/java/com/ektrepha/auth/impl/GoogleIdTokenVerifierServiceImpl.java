package com.ektrepha.auth.impl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ektrepha.exception.InvalidGoogleTokenException;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.auth.security.GoogleIdTokenVerifierService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GoogleIdTokenVerifierServiceImpl implements GoogleIdTokenVerifierService {

	private final GoogleIdTokenVerifier verifier;

	public GoogleIdTokenVerifierServiceImpl(AppProperties appProperties) {
		List<String> audience = Collections.singletonList(appProperties.google().clientId());
		try {
			this.verifier = new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
					.setAudience(audience)
					.build();
		} catch (GeneralSecurityException | IOException ex) {
			throw new IllegalStateException("Failed to initialize Google ID token verifier", ex);
		}
	}

	@Override
	public GoogleIdentity verify(String idTokenString) {
		GoogleIdToken idToken;
		try {
			idToken = verifier.verify(idTokenString);
		} catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
			log.warn("Google ID token verification threw: {}", ex.getMessage());
			throw new InvalidGoogleTokenException("Could not verify Google ID token", ex);
		}

		if (idToken == null) {
			log.warn("Google ID token failed verification (signature/audience/expiry mismatch)");
			throw new InvalidGoogleTokenException("Google ID token is invalid or expired");
		}

		GoogleIdToken.Payload payload = idToken.getPayload();
		String name = (String) payload.get("name");
		log.debug("Verified Google ID token: googleId={}, email={}", payload.getSubject(), payload.getEmail());
		return new GoogleIdentity(payload.getSubject(), payload.getEmail(), name);
	}

}

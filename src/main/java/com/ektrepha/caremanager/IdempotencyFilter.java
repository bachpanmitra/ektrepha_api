package com.ektrepha.caremanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.ektrepha.caremanager.dto.response.CmErrorResponse;
import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.model.IdempotencyKey;
import com.ektrepha.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotency-Key handling for the Care Manager feature's POST/PUT endpoints (docs/api/openapi.yaml
 * — "stored for 24h, same response on replay"). A no-op whenever the header is absent, so this is
 * safe to register globally without affecting any other endpoint in the app, none of which ever
 * sends this header.
 * <p>
 * Positioned after {@link com.ektrepha.auth.security.JwtAuthenticationFilter} (SecurityConfig) so
 * the authenticated caller's id is available to key on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

	private static final String HEADER = "Idempotency-Key";

	private final IdempotencyKeyRepository idempotencyKeyRepository;

	// No ObjectMapper bean is registered app-wide in this Spring Boot version — see
	// BookingEventService/ElderServiceImpl for the same pattern.
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER);
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		if (key == null || key.isBlank() || (method != HttpMethod.POST && method != HttpMethod.PUT)) {
			filterChain.doFilter(request, response);
			return;
		}

		Long userId = resolveUserId();
		if (userId == null) {
			// No authenticated caller yet (e.g. the token was rejected) — let the request proceed
			// so the normal 401 path handles it; there's no one to key the idempotency row on.
			filterChain.doFilter(request, response);
			return;
		}

		ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, 65536);
		// Force the body to be read/cached now, before any downstream code (including this method)
		// consumes the stream, since ContentCachingRequestWrapper only buffers what's actually read.
		cachedRequest.getInputStream().readAllBytes();
		String requestHash = hash(method + ":" + request.getRequestURI() + ":" + new String(cachedRequest.getContentAsByteArray(), StandardCharsets.UTF_8));

		IdempotencyKey.Id id = IdempotencyKey.Id.builder().key(key).userId(userId).build();
		Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(id);
		if (existing.isPresent()) {
			IdempotencyKey stored = existing.get();
			if (stored.getRequestHash().equals(requestHash)) {
				replay(response, stored);
			} else {
				writeDuplicateRequestError(response);
			}
			return;
		}

		ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
		filterChain.doFilter(cachedRequest, cachedResponse);

		byte[] body = cachedResponse.getContentAsByteArray();
		try {
			idempotencyKeyRepository.save(IdempotencyKey.builder()
					.id(id)
					.requestHash(requestHash)
					.statusCode((short) cachedResponse.getStatus())
					.response(new String(body, StandardCharsets.UTF_8))
					.createdAt(Instant.now())
					.build());
		} catch (Exception ex) {
			// Never fail the caller's (already-succeeded) request because the idempotency record
			// itself couldn't be written — a lost record just means a retry re-executes, no worse
			// than not having this feature at all.
			log.warn("Failed to persist idempotency key for user {}: {}", userId, ex.getMessage());
		}
		cachedResponse.copyBodyToResponse();
	}

	private Long resolveUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		try {
			return Long.valueOf(authentication.getName());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private void replay(HttpServletResponse response, IdempotencyKey stored) throws IOException {
		response.setStatus(stored.getStatusCode());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(stored.getResponse());
	}

	private void writeDuplicateRequestError(HttpServletResponse response) throws IOException {
		response.setStatus(409);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String requestId = MDC.get(SecurityConstants.TRACE_ID_MDC_KEY);
		response.getWriter().write(objectMapper.writeValueAsString(
				CmErrorResponse.of("DUPLICATE_REQUEST", "Same Idempotency-Key with a different request body", requestId, null)));
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

}

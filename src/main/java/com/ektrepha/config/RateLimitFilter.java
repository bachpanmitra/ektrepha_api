package com.ektrepha.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ektrepha.config.properties.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coarse, per-client-IP throttle applied to every request before it reaches auth/business logic —
 * see {@link RateLimiterServiceImpl} for the token-bucket mechanics and app.rate-limit for config.
 * This is a blunt, IP-level guard against floods/scraping; it's independent of (and in front of)
 * {@code LoginAttemptService}'s per-identifier login lockout, which still applies on top of it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiterService rateLimiterService;
	private final AppProperties appProperties;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!appProperties.rateLimit().enabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		String clientIp = resolveClientIp(request);
		if (!rateLimiterService.tryConsume(clientIp)) {
			log.warn("Rate limit exceeded for IP {} on {} {}", clientIp, request.getMethod(), request.getRequestURI());
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(appProperties.rateLimit().windowSeconds()));
			response.getWriter().write(
					"{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
			return;
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * X-Forwarded-For is only trustworthy when every request actually passes through our own
	 * reverse proxy/load balancer, which is expected to overwrite (not append to) this header
	 * before forwarding — otherwise a caller can spoof it to dodge the limit entirely by claiming
	 * a fresh IP on every request. Falls back to the socket address for direct connections (e.g.
	 * local/dev).
	 */
	private String resolveClientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

}

package com.ektrepha.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ektrepha.config.constants.SecurityConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
			try {
				Claims claims = jwtService.parseAndValidate(header.substring(SecurityConstants.BEARER_PREFIX.length()));
				String userId = claims.getSubject();
				String userType = claims.get(SecurityConstants.CLAIM_USER_TYPE, String.class);

				List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(SecurityConstants.ROLE_AUTHORITY_PREFIX + userType));
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(userId, null, authorities);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
				log.debug("Authenticated request to {} as userId={}, userType={}", request.getRequestURI(), userId, userType);
			} catch (JwtException | IllegalArgumentException ex) {
				// Routine (expired tokens, stale clients) — debug level so it doesn't spam
				// production logs, but is there the moment someone raises the log level to
				// chase a "why am I getting 401s" report.
				log.debug("Rejected access token on {}: {}", request.getRequestURI(), ex.getMessage());
				SecurityContextHolder.clearContext();
			}
		}

		filterChain.doFilter(request, response);
	}

}

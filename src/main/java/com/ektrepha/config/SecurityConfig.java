package com.ektrepha.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ektrepha.auth.security.JwtAuthenticationFilter;
import com.ektrepha.caremanager.IdempotencyFilter;
import com.ektrepha.config.constants.SecurityConstants;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final TraceIdFilter traceIdFilter;
	private final RateLimitFilter rateLimitFilter;
	private final IdempotencyFilter idempotencyFilter;

	@Value("${app.cors.allowed-origins}")
	private String allowedOriginsCsv;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, TraceIdFilter traceIdFilter,
			RateLimitFilter rateLimitFilter, IdempotencyFilter idempotencyFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.traceIdFilter = traceIdFilter;
		this.rateLimitFilter = rateLimitFilter;
		this.idempotencyFilter = idempotencyFilter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/health", "/api/version", "/actuator/**", "/error", "/api/v1/auth/**").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/search").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/live-zones").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/localities").permitAll()
						// Home page carousel (PRD: Admin Home Page Banner Management) - read by anonymous visitors.
						.requestMatchers(HttpMethod.GET, "/api/v1/home/banners").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/serviceability/waitlist", "/api/waitlist", "/api/v1/waitlist").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/pricing/calculate").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/users/identify").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/booking-requests").permitAll()
						// Razorpay's own signature on the raw body is this endpoint's only auth -
						// see RazorpayWebhookController.
						.requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/**").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/nanny-verification/**").hasAnyRole("NANNY", "ADMIN")
						.requestMatchers("/api/v1/parents/me/**").hasRole("PARENT")
						.requestMatchers("/api/v1/parents/me/elders/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/hourly-care/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/hourly-care/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/nanny-bookings/**").hasRole("NANNY")
					.requestMatchers(HttpMethod.GET, "/api/v1/nanny-bookings/**").hasRole("NANNY")
						.requestMatchers("/api/v1/nanny-leave/**").hasRole("NANNY")
						.requestMatchers("/api/v1/nanny-sos/**").hasRole("NANNY")
						.requestMatchers("/api/v1/incidents/**").hasAnyRole("NANNY", "PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/locations/reverse-geocode").hasRole("PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/locations/autocomplete", "/api/v1/locations/place-details").hasRole("PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/nanny-search/languages", "/api/v1/nanny-search/skills").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/nanny-search").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/reviews").hasRole("PARENT")
						.requestMatchers(HttpMethod.PUT, "/api/v1/nannies/me/service-area").hasRole("NANNY")
						.requestMatchers(HttpMethod.GET, "/api/v1/nannies/**").hasRole("PARENT")
						// Care Manager feature (docs/api/openapi.yaml) — mounted at /v1/... exactly as the
						// contract specifies, deliberately not under /api/v1 like every other controller
						// here; the brief is explicit that paths must match the contract exactly.
						.requestMatchers("/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/v1/cm/**").hasAnyRole("CARE_MANAGER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/v1/me/care-manager").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/v1/me/care-manager/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/v1/bookings/*/ratings", "/v1/bookings/*/nanny-change-requests").hasRole("PARENT")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint()))
				// JwtAuthenticationFilter must be registered (and get an order assigned)
				// before it can be used as the anchor for placing traceIdFilter ahead of it.
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(traceIdFilter, JwtAuthenticationFilter.class)
				// Rate limiting runs first so a throttled request is rejected before any
				// tracing/auth work is done on it.
				.addFilterBefore(rateLimitFilter, TraceIdFilter.class)
				// After auth so it can key on the caller's id; a no-op without Idempotency-Key.
				.addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Arrays.asList(allowedOriginsCsv.split(",")));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setExposedHeaders(List.of("Authorization"));
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	AuthenticationEntryPoint restAuthenticationEntryPoint() {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			// This runs inside Spring Security's ExceptionTranslationFilter, before the request ever
			// reaches a controller, so CareManagerExceptionHandler (a @ControllerAdvice) can't intercept
			// it — the Care Manager feature's /v1/** paths need their own nested shape right here.
			if (request.getRequestURI().startsWith("/v1/")) {
				String requestId = MDC.get(SecurityConstants.TRACE_ID_MDC_KEY);
				response.getWriter().write(String.format(
						"{\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Missing or invalid access token\",\"request_id\":%s}}",
						requestId == null ? "null" : "\"" + requestId + "\""));
			} else {
				response.getWriter().write(
						"{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}");
			}
		};
	}

}

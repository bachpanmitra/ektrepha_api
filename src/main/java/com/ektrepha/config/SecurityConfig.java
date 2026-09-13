package com.ektrepha.config;

import java.util.Arrays;
import java.util.List;

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

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final TraceIdFilter traceIdFilter;
	private final RateLimitFilter rateLimitFilter;

	@Value("${app.cors.allowed-origins}")
	private String allowedOriginsCsv;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, TraceIdFilter traceIdFilter,
			RateLimitFilter rateLimitFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.traceIdFilter = traceIdFilter;
		this.rateLimitFilter = rateLimitFilter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/health", "/api/version", "/actuator/**", "/error", "/api/v1/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/search").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/live-zones").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/serviceability/localities").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/serviceability/waitlist", "/api/waitlist", "/api/v1/waitlist").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/pricing/calculate").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/users/identify").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/booking-requests").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/nanny-verification/**").hasAnyRole("NANNY", "ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/**").hasRole("PARENT")
						.requestMatchers(HttpMethod.GET, "/api/v1/nanny-search/languages", "/api/v1/nanny-search/skills").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/nanny-search").hasRole("PARENT")
						.requestMatchers(HttpMethod.POST, "/api/v1/reviews").hasRole("PARENT")
						.requestMatchers(HttpMethod.PUT, "/api/v1/nannies/me/service-area").hasRole("NANNY")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint()))
				// JwtAuthenticationFilter must be registered (and get an order assigned)
				// before it can be used as the anchor for placing traceIdFilter ahead of it.
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(traceIdFilter, JwtAuthenticationFilter.class)
				// Rate limiting runs first so a throttled request is rejected before any
				// tracing/auth work is done on it.
				.addFilterBefore(rateLimitFilter, TraceIdFilter.class);
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
			response.getWriter().write(
					"{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}");
		};
	}

}

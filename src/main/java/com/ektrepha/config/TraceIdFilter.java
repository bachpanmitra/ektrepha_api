package com.ektrepha.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ektrepha.config.constants.SecurityConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps every request with a trace ID (reusing one supplied via the
 * X-Trace-Id header, e.g. from a reverse proxy, or generating one) so all
 * log lines for a single request can be tied together via %X{traceId} in
 * logback-spring.xml. Echoed back on the response so a caller reporting an
 * issue can hand you the exact ID to search for.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String traceId = request.getHeader(SecurityConstants.TRACE_ID_HEADER);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString();
		}

		MDC.put(SecurityConstants.TRACE_ID_MDC_KEY, traceId);
		response.setHeader(SecurityConstants.TRACE_ID_HEADER, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			// Threads are pooled — always clear, or the next request on this thread inherits a stale ID.
			MDC.remove(SecurityConstants.TRACE_ID_MDC_KEY);
		}
	}

}

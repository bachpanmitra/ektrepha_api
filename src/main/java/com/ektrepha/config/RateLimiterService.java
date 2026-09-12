package com.ektrepha.config;

/** Per-key token bucket used to throttle requests. See {@link RateLimiterServiceImpl}. */
public interface RateLimiterService {

	/**
	 * @return true if a request for {@code key} may proceed (a token was consumed), false if the
	 *         key is currently over its rate limit
	 */
	boolean tryConsume(String key);

}

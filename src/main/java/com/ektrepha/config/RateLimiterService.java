package com.ektrepha.config;

/** Per-key token bucket used to throttle requests. See {@link RateLimiterServiceImpl}. */
public interface RateLimiterService {

	/**
	 * @return true if a request for {@code key} may proceed (a token was consumed), false if the
	 *         key is currently over its rate limit
	 */
	boolean tryConsume(String key);

	/**
	 * Same as {@link #tryConsume(String)}, but with a caller-supplied capacity/window instead of
	 * app.rate-limit's global per-IP defaults — for a limiter with its own, separately-configured
	 * bucket (e.g. per-phone-number OTP request throttling) sharing this same key space.
	 */
	boolean tryConsume(String key, int capacity, long windowSeconds);

}

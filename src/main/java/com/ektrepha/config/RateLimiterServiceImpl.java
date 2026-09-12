package com.ektrepha.config;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * In-memory token bucket per key (client IP — see {@link RateLimitFilter}), per app.rate-limit
 * config. Same tradeoff as {@code LoginAttemptServiceImpl}: no Redis wired into the app yet, so
 * this only throttles per instance. Fine for a single instance; would need Redis (or a shared
 * store) to enforce one global limit per IP across multiple instances.
 * <p>
 * Buckets are kept in a bounded, self-evicting Caffeine cache rather than a plain map so that
 * churn through many distinct (or spoofed) IPs can't grow this unbounded.
 */
@Component
public class RateLimiterServiceImpl implements RateLimiterService {

	private static final long BUCKET_IDLE_EVICTION_SECONDS = 300;
	private static final long BUCKET_CACHE_MAX_ENTRIES = 100_000;

	private static final class TokenBucket {
		private double tokens;
		private long lastRefillNanos;

		TokenBucket(double tokens, long lastRefillNanos) {
			this.tokens = tokens;
			this.lastRefillNanos = lastRefillNanos;
		}

		synchronized boolean tryConsume(double capacity, double refillTokensPerNano) {
			long now = System.nanoTime();
			long elapsedNanos = now - lastRefillNanos;
			if (elapsedNanos > 0) {
				tokens = Math.min(capacity, tokens + elapsedNanos * refillTokensPerNano);
				lastRefillNanos = now;
			}
			if (tokens >= 1.0) {
				tokens -= 1.0;
				return true;
			}
			return false;
		}
	}

	private final AppProperties appProperties;
	private final Cache<String, TokenBucket> buckets;

	public RateLimiterServiceImpl(AppProperties appProperties) {
		this.appProperties = appProperties;
		this.buckets = Caffeine.newBuilder()
				.maximumSize(BUCKET_CACHE_MAX_ENTRIES)
				.expireAfterAccess(Duration.ofSeconds(BUCKET_IDLE_EVICTION_SECONDS))
				.build();
	}

	@Override
	public boolean tryConsume(String key) {
		AppProperties.RateLimit config = appProperties.rateLimit();
		double capacity = config.capacity();
		double refillTokensPerNano = capacity / (config.windowSeconds() * 1_000_000_000.0);

		TokenBucket bucket = buckets.get(key, k -> new TokenBucket(capacity, System.nanoTime()));
		return bucket.tryConsume(capacity, refillTokensPerNano);
	}

}

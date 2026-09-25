package com.ektrepha.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ektrepha.config.properties.AppProperties;

class RateLimiterServiceImplTest {

	private RateLimiterService newLimiter(int capacity, long windowSeconds) {
		AppProperties.RateLimit rateLimit = new AppProperties.RateLimit(true, capacity, windowSeconds);
		AppProperties appProperties = new AppProperties(null, null, null, rateLimit, null, null, null, null, null, null, null, null, null, null, null, null);
		return new RateLimiterServiceImpl(appProperties);
	}

	@Test
	void allowsRequestsUpToCapacityThenBlocks() {
		RateLimiterService limiter = newLimiter(3, 60);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
	}

	@Test
	void tracksEachKeyIndependently() {
		RateLimiterService limiter = newLimiter(1, 60);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
		assertThat(limiter.tryConsume("5.6.7.8")).isTrue();
	}

	@Test
	void refillsTokensOverTime() throws InterruptedException {
		RateLimiterService limiter = newLimiter(1, 1);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();

		Thread.sleep(1100);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
	}

}

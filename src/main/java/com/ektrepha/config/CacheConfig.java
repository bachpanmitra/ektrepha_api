package com.ektrepha.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Proxy-pattern caching (Spring wraps {@code @Cacheable} methods in a transparent caching proxy)
 * for the serviceability/pricing hot paths - pincode/coordinate-&gt;zone resolution and
 * zone+service-type pricing lookups are read on every search/quote request (design target:
 * ~1M/day) but only change when an admin edits them.
 * <p>
 * In-process Caffeine, not Redis - this app runs as a single instance today (see
 * {@code LoginAttemptServiceImpl}); the redis service in
 * docker-compose isn't wired into the app yet. If this ever runs behind multiple instances, these
 * caches (or an eviction broadcast) would need to move to Redis so a write on one instance
 * invalidates the others.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String ZONE_BY_PINCODE = "zoneByPincode";
	public static final String ZONE_BY_COORDINATES = "zoneByCoordinates";
	public static final String PRICING_BY_ZONE_SERVICE = "pricingByZoneService";
	public static final String SERVICE_TYPE_STATUS = "serviceTypeStatus";

	@Bean
	CacheManager cacheManager() {
		CaffeineCacheManager manager = new CaffeineCacheManager(
				ZONE_BY_PINCODE, ZONE_BY_COORDINATES, PRICING_BY_ZONE_SERVICE, SERVICE_TYPE_STATUS);
		manager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(5_000)
				.expireAfterWrite(5, TimeUnit.MINUTES));
		return manager;
	}

}

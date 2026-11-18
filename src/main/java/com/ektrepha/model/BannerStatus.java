package com.ektrepha.model;

/** Never persisted — always computed from HomeBanner.isActive/startAt/endAt at read time (PRD: Home Banner Management). */
public enum BannerStatus {
	DRAFT,
	SCHEDULED,
	LIVE,
	EXPIRED,
	INACTIVE
}

package com.ektrepha.trial.impl;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ektrepha.trial.service.TrialAnalyticsService;

/**
 * Logs each event as a single structured line through a dedicated {@code ANALYTICS} logger,
 * rather than calling out to a real analytics vendor — none exists anywhere in this codebase
 * today. Whatever log pipeline this app already ships to can filter on the {@code ANALYTICS}
 * logger name to pick these up; replacing this with a real sink (Segment/Mixpanel/PostHog/...)
 * later is a one-class change, since every caller only depends on {@link TrialAnalyticsService}.
 */
@Service
public class TrialAnalyticsServiceImpl implements TrialAnalyticsService {

	private static final Logger ANALYTICS_LOG = LoggerFactory.getLogger("ANALYTICS");

	@Override
	public void track(String event, Map<String, Object> properties) {
		// TreeMap for a stable, deterministic key order in the log line regardless of caller-supplied
		// map order - makes grepping/diffing log lines for the same event sane.
		ANALYTICS_LOG.info("event={} properties={}", event, new TreeMap<>(properties));
	}

}
